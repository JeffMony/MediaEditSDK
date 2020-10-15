package com.video.process;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.video.process.exception.VideoProcessException;
import com.video.process.listener.IVideoProcessListener;
import com.video.process.model.ProcessParams;
import com.video.process.model.TrackType;
import com.video.process.model.VideoRange;
import com.video.process.utils.LogUtils;
import com.video.process.utils.MediaUtils;
import com.video.process.utils.WorkThreadHandler;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class VideoProcessorManager {

    private static VideoProcessorManager sInstance = null;
    private Context mContext;

    private VideoProcessorManager() { }

    public static VideoProcessorManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoProcessorManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoProcessorManager();
                }
            }
        }
        return sInstance;
    }

    public void initContext(@NonNull Context context) {
        mContext = context;
    }

    /**
     * 反转视频
     * @param inputPath
     * @param outputPath
     * @param shouldReverseAudio
     * @param listener
     */
    public void reverseVideo(String inputPath, String outputPath, boolean shouldReverseAudio, @NonNull IVideoProcessListener listener) {
        if (TextUtils.isEmpty(inputPath) || TextUtils.isEmpty(outputPath)) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_INPUT_OR_OUTPUT_PATH, VideoProcessException.ERR_INPUT_OR_OUTPUT_PATH));
            return;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputPath);
        } catch (Exception e) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_EXTRACTOR_SET_DATASOURCE, VideoProcessException.ERR_EXTRACTOR_SET_DATASOURCE));
            MediaUtils.closeExtractor(extractor);
            return;
        }
        int videoTrackIndex = MediaUtils.getTrackIndex(extractor, TrackType.VIDEO);
        if (videoTrackIndex == MediaUtils.ERR_NO_TRACK_INDEX) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_NO_VIDEO_TRACK, VideoProcessException.ERR_NO_VIDEO_TRACK));
            MediaUtils.closeExtractor(extractor);
            return;
        }
        extractor.selectTrack(videoTrackIndex);
        int keyFrameCount = 0;
        int frameCount = 0;
        List<Long> frameTimeStamps = new ArrayList<>();
        while(true) {
            int flags = extractor.getSampleFlags();
            if (flags > 0 && (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                keyFrameCount++;
            }
            long sampleTime = extractor.getSampleTime();
            if (sampleTime < 0) {
                break;
            }
            frameTimeStamps.add(sampleTime);
            frameCount++;
            extractor.advance();
        }
        MediaUtils.closeExtractor(extractor);

        //如果视频中都是关键帧
        if (frameCount == keyFrameCount || frameCount == keyFrameCount + 1) {
            directReverseVideo(inputPath, outputPath, shouldReverseAudio, frameTimeStamps, listener);
        }
    }

    //直接转换完全关键帧的视频
    private void directReverseVideo(String inputPath, String outputPath, boolean shouldReverseAudio, List<Long> frameTimeStamps, @NonNull IVideoProcessListener listener) {
        if (frameTimeStamps == null || frameTimeStamps.size() <= 0) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_INPUT_VIDEO_NO_KEYFRAME, VideoProcessException.ERR_INPUT_VIDEO_NO_KEYFRAME));
            return;
        }

        int duration = MediaUtils.getDuration(inputPath);
        if (duration == -1) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_INPUT_VIDEO_NO_DURATION, VideoProcessException.ERR_INPUT_VIDEO_NO_DURATION));
            return;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputPath);
        } catch (Exception e) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_EXTRACTOR_SET_DATASOURCE, VideoProcessException.ERR_EXTRACTOR_SET_DATASOURCE));
            MediaUtils.closeExtractor(extractor);
            return;
        }
        int videoTrackIndex = MediaUtils.getTrackIndex(extractor, TrackType.VIDEO);
        int audioTrackIndex = MediaUtils.getTrackIndex(extractor, TrackType.AUDIO);

        //判断是否存在视频轨道
        if (videoTrackIndex == MediaUtils.ERR_NO_TRACK_INDEX) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_NO_VIDEO_TRACK, VideoProcessException.ERR_NO_VIDEO_TRACK));
            MediaUtils.closeExtractor(extractor);
            return;
        }

        //判断是否存在音频轨道
        if (audioTrackIndex == MediaUtils.ERR_NO_TRACK_INDEX) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_NO_AUDIO_TRACK, VideoProcessException.ERR_NO_AUDIO_TRACK));
            MediaUtils.closeExtractor(extractor);
            return;
        }

        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_MEDIAMUXER_CREATE_FAILED, VideoProcessException.ERR_MEDIAMUXER_CREATE_FAILED));
            MediaUtils.closeExtractor(extractor);
            MediaUtils.closeMuxer(muxer);
            return;
        }
        extractor.selectTrack(videoTrackIndex);
        MediaFormat videoFormat = extractor.getTrackFormat(videoTrackIndex);

        int videoDuration = videoFormat.getInteger(MediaFormat.KEY_DURATION);
        int audioDuration = 0;
        int videoMuxerIndex = muxer.addTrack(videoFormat);
        int audioMuxerIndex = 0;
        if (shouldReverseAudio) {
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
            audioDuration = audioFormat.getInteger(MediaFormat.KEY_DURATION);
            audioMuxerIndex = muxer.addTrack(audioFormat);
        }
        muxer.start();

        int maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(maxBufferSize);

        MediaUtils.seekToLastFrame(extractor, videoTrackIndex, duration);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        long lastFrameTime = -1;

        //处理视频
        for (int index = frameTimeStamps.size() - 1; index >= 0; index--) {
            extractor.seekTo(frameTimeStamps.get(index), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            long sampleTime = extractor.getSampleTime();
            if (lastFrameTime == -1) {
                lastFrameTime = sampleTime;
            }
            bufferInfo.presentationTimeUs = lastFrameTime - sampleTime;
            bufferInfo.size = extractor.readSampleData(byteBuffer, 0);
            bufferInfo.flags = extractor.getSampleFlags();

            if (bufferInfo.size < 0) {
                break;
            }

            muxer.writeSampleData(videoMuxerIndex, byteBuffer, bufferInfo);
            float videoProcessProgress = bufferInfo.presentationTimeUs * 1.0f / videoDuration;
            videoProcessProgress = (videoProcessProgress - 1.0f) > 0.00001 ? 1.0f : videoProcessProgress;
            videoProcessProgress *= MediaUtils.VIDEO_WEIGHT;
            listener.onVideoReverseProgress(videoProcessProgress);
        }


        //处理音频
        extractor.unselectTrack(videoTrackIndex);
        extractor.selectTrack(audioTrackIndex);

        //需要反转音频轨道
        if (shouldReverseAudio) {
            List<Long> audioFrameTimeStamps = MediaUtils.getFrameTimeStamps(extractor);
            lastFrameTime = -1;

            for (int index = audioFrameTimeStamps.size() - 1; index >= 0; index--) {
                extractor.seekTo(audioFrameTimeStamps.get(index), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                long sampleTime = extractor.getSampleTime();
                if (lastFrameTime == -1) {
                    lastFrameTime = sampleTime;
                }
                bufferInfo.presentationTimeUs = lastFrameTime - sampleTime;
                bufferInfo.size = extractor.readSampleData(byteBuffer, 0);
                bufferInfo.flags = extractor.getSampleFlags();

                if (bufferInfo.size < 0) {
                    break;
                }
                muxer.writeSampleData(audioMuxerIndex, byteBuffer, bufferInfo);

                float audioProcessProgress = bufferInfo.presentationTimeUs * 1.0f / audioDuration;
                audioProcessProgress = (audioProcessProgress - 1.0f) > 0.00001 ? 1.0f : audioProcessProgress;
                audioProcessProgress = audioProcessProgress * MediaUtils.AUDIO_WEIGHT + MediaUtils.VIDEO_WEIGHT;
                listener.onVideoReverseProgress(audioProcessProgress);
            }
        } else {
            //不需要反转音频轨道
        }
    }


    public void replaceAudioTrack(@NonNull final ProcessParams params) {
        WorkThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                AudioProcessorManager.getInstance().replaceAudioTrack(params.mInputVideoPath, params.mInputAudioPath, params.mOutputVideoPath, true);
            }
        });
    }

    public void addAudioFilter(@NonNull ProcessParams params) {
        //1.抽取输入视频的音频轨道
        MediaExtractor inputVideoExtractor = new MediaExtractor();
        try {
            inputVideoExtractor.setDataSource(params.mInputVideoPath);
        } catch (Exception e) {
            LogUtils.w("addAudioFilter failed, input video path MediaExtractor failed");
            MediaUtils.closeExtractor(inputVideoExtractor);
            return;
        }
        int srcInputAudioIndex = MediaUtils.getTrackIndex(inputVideoExtractor, TrackType.AUDIO);
        if (srcInputAudioIndex == -5) {
            LogUtils.w("addAudioFilter failed, input video has no audio track");
            MediaUtils.closeExtractor(inputVideoExtractor);
            return;
        }

        //2.抽取输入视频的视频轨道
        int srcInputVideoIndex = MediaUtils.getTrackIndex(inputVideoExtractor, TrackType.VIDEO);
        if (srcInputVideoIndex == -5) {
            LogUtils.w("addAudioFilter failed, input video has no video track");
            MediaUtils.closeExtractor(inputVideoExtractor);
            return;
        }

        //3.抽取输入音频的音频轨道
        MediaExtractor inputAudioExtractor = new MediaExtractor();
        try {
            inputAudioExtractor.setDataSource(params.mInputAudioPath);
        } catch (Exception e) {
            LogUtils.w("addAudioFilter failed, input audio path MediaExtractor failed");
            MediaUtils.closeExtractor(inputVideoExtractor);
            MediaUtils.closeExtractor(inputAudioExtractor);
            return;
        }
        int destInputAudioIndex = MediaUtils.getTrackIndex(inputAudioExtractor, TrackType.AUDIO);
        if (destInputAudioIndex == -5) {
            LogUtils.w("addAudioFilter failed, input audio has no audio track");
            MediaUtils.closeExtractor(inputVideoExtractor);
            MediaUtils.closeExtractor(inputAudioExtractor);
            return;
        }

        //4.开始合成视频
        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(params.mOutputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            MediaUtils.closeExtractor(inputVideoExtractor);
            MediaUtils.closeExtractor(inputAudioExtractor);
            MediaUtils.closeMuxer(mediaMuxer);
            return;
        }

        MediaFormat srcInputVideoFormat = MediaUtils.getFormat(inputVideoExtractor, srcInputVideoIndex);
        int rotation = MediaUtils.getRotation(inputVideoExtractor);
        mediaMuxer.setOrientationHint(rotation);
        int muxerVideoTrackIndex = mediaMuxer.addTrack(srcInputVideoFormat);

        //5.将视频中的音频取出来
        File cacheDir = new File(mContext.getCacheDir(), "audio");
        long timeStamp = System.currentTimeMillis();
        //srcInputAudioFile 将视频中分离的音频保存的位置
        File srcInputAudioFile = new File(cacheDir, "video_" + timeStamp + "_audio" + ".pcm");
        //inputAudioFile 输入的音频转化为pcm
        File inputAudioFile = new File(cacheDir, "audio_" + timeStamp + ".pcm");

        long inputVideoDuration = MediaUtils.getDuration(params.mInputVideoPath);
        if (inputVideoDuration == -1) {
            LogUtils.i("addAudioFilter failed, Input invalid video path");
            return;
        }
        long inputAudioDuration = MediaUtils.getDuration(params.mInputAudioPath);
        if (inputAudioDuration == -1) {
            LogUtils.i("addAudioFilter failed, Input invalid audio path");
            return;
        }
        VideoRange range = params.mVideoRange;
        if (range == null) {
            range = new VideoRange(0, inputVideoDuration);
            params.setVideoRange(range);
        }
        if (range.mStart < 0) {
            range.mStart = 0;
        } else if (range.mEnd < range.mStart) {
            LogUtils.i("The input video range is error");
            return;
        } else if (inputVideoDuration < (range.mEnd - range.mStart)) {
            range.mEnd = inputVideoDuration;
            if (range.mEnd < range.mStart) {
                LogUtils.i("The input video range is error");
                return;
            }
        }
        params.setVideoRange(range);

        final CountDownLatch latch = new CountDownLatch(2);
        WorkThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {

                latch.countDown();
            }
        });
    }

}
