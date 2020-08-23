package com.video.process.compose.video;

import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;

import com.video.process.model.ProcessParams;
import com.video.process.model.VideoRange;
import com.video.process.compose.audio.AudioComposer;
import com.video.process.compose.audio.IAudioComposer;
import com.video.process.compose.audio.RemixAudioComposer;
import com.video.process.utils.VideoCustomException;

import java.io.FileDescriptor;

// Refer: https://github.com/ypresto/android-transcoder/blob/master/lib/src/main/java/net/ypresto/androidtranscoder/engine/MediaTranscoderEngine.java

/**
 * Internal engine, do not use this directly.
 */
public class Mp4ComposerEngine {
    private static final String TAG = Mp4ComposerEngine.class.getSimpleName();
    private static final double PROGRESS_UNKNOWN = -1.0;
    private static final long SLEEP_TO_WAIT_TRACK_TRANSCODERS = 10;
    private static final long PROGRESS_INTERVAL_STEPS = 10;
    private ComposeProgressCallback mProgressCallback;
    private FileDescriptor mInputFd;
    private VideoComposer mVideoComposer;
    private IAudioComposer mAudioComposer;
    private MediaExtractor mMediaExtractor;
    private MediaMuxer mMediaMuxer;
    private long mComposeDuration;

    public void setDataSource(FileDescriptor fd) {
        mInputFd = fd;
    }

    public void setProgressCallback(ComposeProgressCallback progressCallback) {
        mProgressCallback = progressCallback;
    }

    public void compose(ProcessParams processParams) throws VideoCustomException {
        MediaMetadataRetriever mediaRetriever = new MediaMetadataRetriever();
        mediaRetriever.setDataSource(mInputFd);
        long duration = Long.parseLong(mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        VideoRange range = processParams.mVideoRange;
        if (range == null) {
            range = new VideoRange(0, duration);
            processParams.setVideoRange(range);
        }
        if (range.mStart < 0) {
            range.mStart = 0;
        } else if (range.mEnd < range.mStart) {
            if (mediaRetriever != null) {
                mediaRetriever.release();
            }
            throw new VideoCustomException(VideoCustomException.CLIP_VIDEO_TIMERANGE_ERROR, new Throwable());
        } else if (duration < (range.mEnd - range.mStart)) {
            range.mEnd = duration;
            if (range.mEnd < range.mStart) {
                if (mediaRetriever != null) {
                    mediaRetriever.release();
                }
                throw new VideoCustomException(VideoCustomException.CLIP_VIDEO_OUT_OF_RANGE, new Throwable());
            }
        }
        processParams.setVideoRange(range);
        mComposeDuration = range.mEnd - range.mStart;

        mMediaExtractor = new MediaExtractor();
        try {
            mMediaExtractor.setDataSource(mInputFd);
        } catch (Exception e) {
            if (mediaRetriever != null) {
                mediaRetriever.release();
            }
            releaseMediaResources();
            throw new VideoCustomException(VideoCustomException.MEDIA_EXTRACTOR_DATASOURCE_FAILED, e);
        }
        try {
            mMediaMuxer = new MediaMuxer(processParams.mOutputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            if (mediaRetriever != null) {
                mediaRetriever.release();
            }
            releaseMediaResources();
            throw new VideoCustomException(VideoCustomException.MEDIA_MUXER_INSTANCE_FAILED, e);
        }
        MediaFormat videoOutputFormat = MediaFormat.createVideoFormat("video/avc", processParams.mOutputVideoSize.mWidth, processParams.mOutputVideoSize.mHeight);
        videoOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE, processParams.mBitRate);
        videoOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, processParams.mFrameRate);
        videoOutputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        videoOutputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        //获取音视频的轨道
        int videoTrackIndex = -1;
        int audioTrackIndex = -1;

        int trackCount = mMediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mMediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if(mime.startsWith("video/")) {
                videoTrackIndex = i;
            } else if (mime.startsWith("audio/")) {
                audioTrackIndex = i;
            }
        }

        if (videoTrackIndex == -1) {
            if (mediaRetriever != null) {
                mediaRetriever.release();
            }
            releaseMediaResources();
            throw new VideoCustomException(VideoCustomException.MEDIA_HAS_NO_VIDEO, new Throwable());
        }
        MuxRender muxRender = new MuxRender(mMediaMuxer);
        mVideoComposer = new VideoComposer(mMediaExtractor, videoTrackIndex, videoOutputFormat, muxRender, processParams.mTimeScale);
        mVideoComposer.setUp(processParams);
        mMediaExtractor.selectTrack(videoTrackIndex);
        mVideoComposer.setClipRange(processParams.mVideoRange.mStart, processParams.mVideoRange.mEnd);

        if (audioTrackIndex != -1 && !processParams.mIsMute) {
            if (processParams.mTimeScale < 2) {
                mAudioComposer = new AudioComposer(mMediaExtractor, audioTrackIndex, muxRender);
                ((AudioComposer) mAudioComposer).setClipRange(processParams.mVideoRange.mStart, processParams.mVideoRange.mEnd);
            } else {
                mAudioComposer = new RemixAudioComposer(mMediaExtractor, audioTrackIndex, mMediaExtractor.getTrackFormat(audioTrackIndex), muxRender, processParams.mTimeScale);
            }
            mAudioComposer.setup();
            mMediaExtractor.selectTrack(audioTrackIndex);
            runComposePipelines();
        } else {
            runComposePipelinesNoAudio();
        }
        mMediaMuxer.stop();
    }

    private void releaseMediaResources() {
        if (mVideoComposer != null) {
            mVideoComposer.release();
            mVideoComposer = null;
        }
        if (mAudioComposer != null) {
            mAudioComposer.release();
            mAudioComposer = null;
        }
        if (mMediaExtractor != null) {
            mMediaExtractor.release();
            mMediaExtractor = null;
        }
        if (mMediaMuxer != null) {
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
    }


    private void runComposePipelines() {
        long loopCount = 0;
        if (mComposeDuration <= 0) {
            if (mProgressCallback != null) {
                mProgressCallback.onProgress(PROGRESS_UNKNOWN);
            }
            return;
        }
        while (!(mVideoComposer.isFinished() && mAudioComposer.isFinished())) {
            boolean stepped = mVideoComposer.stepPipeline()
                    || mAudioComposer.stepPipeline();
            loopCount++;
            if (mComposeDuration > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = mVideoComposer.isFinished() ? 1.0 : Math.min(1.0, (double) mVideoComposer.getWrittenPresentationTimeUs() / mComposeDuration);
                double audioProgress = mAudioComposer.isFinished() ? 1.0 : Math.min(1.0, (double) mAudioComposer.getWrittenPresentationTimeUs() / mComposeDuration);
                double progress = (videoProgress + audioProgress) / 2.0;
                if (mProgressCallback != null) {
                    mProgressCallback.onProgress(progress);
                }
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
                } catch (InterruptedException e) {
                    // nothing to do
                }
            }
        }
    }

    private void runComposePipelinesNoAudio() {
        long loopCount = 0;
        if (mComposeDuration <= 0) {
            if (mProgressCallback != null) {
                mProgressCallback.onProgress(PROGRESS_UNKNOWN);
            }
        }
        while (!mVideoComposer.isFinished()) {
            boolean stepped = mVideoComposer.stepPipeline();
            loopCount++;
            if (mComposeDuration > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = mVideoComposer.isFinished() ? 1.0 : Math.min(1.0, (double) mVideoComposer.getWrittenPresentationTimeUs() / mComposeDuration);
                if (mProgressCallback != null) {
                    mProgressCallback.onProgress(videoProgress);
                }
            }
            if (!stepped) {
                try {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
                } catch (InterruptedException e) {
                    // nothing to do
                    if (mProgressCallback != null) {
//                        mProgressCallback.onFailed(new VideoCustomException());
                    }
                }
            }
        }


    }


    interface ComposeProgressCallback {
        /**
         * progress ---> range[0,1],如果为止进度为负
         * @param progress
         */
        void onProgress(double progress);

        //合成视频成功
        void onCompleted();

        //合成视频失败
        void onFailed(VideoCustomException e);
    }
}
