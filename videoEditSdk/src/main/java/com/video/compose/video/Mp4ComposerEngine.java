package com.video.compose.video;

import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;

import com.video.compose.VideoSize;
import com.video.compose.audio.AudioComposer;
import com.video.compose.audio.IAudioComposer;
import com.video.compose.audio.RemixAudioComposer;
import com.video.epf.filter.GlFilter;
import com.video.egl.GlFilterList;
import com.video.compose.FillMode;
import com.video.compose.CustomFillMode;
import com.video.compose.Rotation;
import com.video.compose.VideoCustomException;

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

    public void compose(
            String destPath,
            VideoSize outputResolution,
            GlFilter filter,
            GlFilterList filterList,
            int bitrate,
            int frameRate,
            boolean mute,
            Rotation rotation,
            VideoSize inputResolution,
            FillMode fillMode,
            CustomFillMode fillModeCustomItem,
            int timeScale,
            boolean flipVertical,
            boolean flipHorizontal,
            long startTimeMs,
            long endTimeMs
    ) throws VideoCustomException {
        MediaMetadataRetriever mediaRetriever = new MediaMetadataRetriever();
        mediaRetriever.setDataSource(mInputFd);
        long duration = Long.parseLong(mediaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        if (startTimeMs <= 0) {
            startTimeMs = 0;
        } else if (endTimeMs < startTimeMs) {
            if (mediaRetriever != null) {
                mediaRetriever.release();
            }
            throw new VideoCustomException(VideoCustomException.CLIP_VIDEO_TIMERANGE_ERROR, new Throwable());
        } else if (duration < (endTimeMs - startTimeMs)) {
            endTimeMs = duration;
            if (endTimeMs < startTimeMs) {
                if (mediaRetriever != null) {
                    mediaRetriever.release();
                }
                throw new VideoCustomException(VideoCustomException.CLIP_VIDEO_OUT_OF_RANGE, new Throwable());
            }
        } else {
            mComposeDuration = endTimeMs - startTimeMs;
        }
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
            mMediaMuxer = new MediaMuxer(destPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            if (mediaRetriever != null) {
                mediaRetriever.release();
            }
            releaseMediaResources();
            throw new VideoCustomException(VideoCustomException.MEDIA_MUXER_INSTANCE_FAILED, e);
        }
        MediaFormat videoOutputFormat = MediaFormat.createVideoFormat("video/avc", outputResolution.mWidth, outputResolution.mHeight);
        videoOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        videoOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
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
        mVideoComposer = new VideoComposer(mMediaExtractor, videoTrackIndex, videoOutputFormat, muxRender, timeScale);
        mVideoComposer.setUp(filter, filterList, rotation, outputResolution, inputResolution, fillMode, fillModeCustomItem, flipVertical, flipHorizontal);
        mMediaExtractor.selectTrack(videoTrackIndex);
        mVideoComposer.setClipRange(startTimeMs, endTimeMs);

        if (audioTrackIndex != -1 && !mute) {
            if (timeScale < 2) {
                mAudioComposer = new AudioComposer(mMediaExtractor, audioTrackIndex, muxRender);
                if (startTimeMs >= 0 && endTimeMs > startTimeMs) {
                    ((AudioComposer) mAudioComposer).setClipRange(startTimeMs, endTimeMs);
                }
            } else {
                mAudioComposer = new RemixAudioComposer(mMediaExtractor, audioTrackIndex, mMediaExtractor.getTrackFormat(audioTrackIndex), muxRender, timeScale);
            }
            mAudioComposer.setup();
            mMediaExtractor.selectTrack(audioTrackIndex);
            runPipelines();
        } else {
            runPipelinesNoAudio();
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


    private void runPipelines() {
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

    private void runPipelinesNoAudio() {
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
