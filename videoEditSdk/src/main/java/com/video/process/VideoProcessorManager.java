package com.video.process;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.video.process.exception.VideoProcessException;
import com.video.process.listener.IVideoProcessorListener;
import com.video.process.listener.IVideoReverseListener;
import com.video.process.model.ProcessParams;
import com.video.process.model.TrackType;
import com.video.process.model.VideoRange;
import com.video.process.thread.AudioProcessorThread;
import com.video.process.thread.VideoDecodeThread;
import com.video.process.thread.VideoEncodeThread;
import com.video.process.utils.AudioUtils;
import com.video.process.utils.LogUtils;
import com.video.process.utils.VideoUtils;
import com.video.process.utils.WorkThreadHandler;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static class ProcessorBuilder {
        private Context mContext;
        private String mInputPath;
        private String mOutputPath;
        private int mOutputWidth;
        private int mOutputHeight;
        private int mStartTimeStamp = -1;
        private int mEndTimeStamp = -1;
        private float mSpeed = 0;
        private boolean mShouldChangedAudioSpeed;
        private int mBitrate;
        private int mFrameRate;
        private int mIFrameInterval = -100;
        private boolean mShouldDropFrame; //帧率超过指定帧率时是否掉帧
        private IVideoProcessorListener mProcessorListener;

        public ProcessorBuilder(Context context) {
            mContext = context;
        }

        public ProcessorBuilder setInputPath(String inputPath) {
            mInputPath = inputPath;
            return this;
        }

        public ProcessorBuilder setOutputPath(String outputPath) {
            mOutputPath = outputPath;
            return this;
        }

        public ProcessorBuilder setOutputWidth(int width) {
            mOutputWidth = width;
            return this;
        }

        public ProcessorBuilder setOutputHeight(int height) {
            mOutputHeight = height;
            return this;
        }

        public ProcessorBuilder setStartTimeStamp(int startTimeStamp) {
            mStartTimeStamp = startTimeStamp;
            return this;
        }

        public ProcessorBuilder setEndTimeStamp(int endTimeStamp) {
            mEndTimeStamp = endTimeStamp;
            return this;
        }

        public ProcessorBuilder setSpeed(float speed) {
            mSpeed = speed;
            return this;
        }

        public ProcessorBuilder setShouldChangedAudioSpeed(boolean enable) {
            mShouldChangedAudioSpeed = enable;
            return this;
        }

        public ProcessorBuilder setBitrate(int bitrate) {
            mBitrate = bitrate;
            return this;
        }

        public ProcessorBuilder setFrameRate(int frameRate) {
            mFrameRate = frameRate;
            return this;
        }

        public ProcessorBuilder setIFrameInterval(int iFrameInterval) {
            mIFrameInterval = iFrameInterval;
            return this;
        }

        public ProcessorBuilder setShouldDropFrame(boolean dropFrame) {
            mShouldDropFrame = dropFrame;
            return this;
        }

        public ProcessorBuilder setProcessorListener(@NonNull IVideoProcessorListener listener) {
            mProcessorListener = listener;
            return this;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void process() {
            doProcessVideo(mContext, this);
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void doProcessVideo(@NonNull Context context, @NonNull ProcessorBuilder processorBuilder) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(processorBuilder.mInputPath);
        int inputWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int inputHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        int inputRotation = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
        int inputBitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        int inputDuration = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        retriever.release();

        if (processorBuilder.mBitrate == 0) {
            processorBuilder.mBitrate = inputBitrate;
        }
        if (processorBuilder.mIFrameInterval == -100) {
            processorBuilder.mIFrameInterval = VideoUtils.DEFAULT_I_FRAME_INTERVAL;
        }

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(processorBuilder.mInputPath);
        } catch (Exception e) {
            processorBuilder.mProcessorListener.onVideoProcessorFailed(new VideoProcessException(VideoProcessException.ERR_STR_EXTRACTOR_SET_DATASOURCE, VideoProcessException.ERR_EXTRACTOR_SET_DATASOURCE));
            VideoUtils.closeExtractor(extractor);
            return;
        }
        int videoTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.VIDEO);
        int audioTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.AUDIO);
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(processorBuilder.mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            processorBuilder.mProcessorListener.onVideoProcessorFailed(new VideoProcessException(VideoProcessException.ERR_STR_MEDIAMUXER_CREATE_FAILED, VideoProcessException.ERR_MEDIAMUXER_CREATE_FAILED));
            VideoUtils.closeExtractor(extractor);
            VideoUtils.closeMuxer(muxer);
            return;
        }

        int muxerAudioIndex = 0;
        int audioEndTimeStamp = processorBuilder.mEndTimeStamp;
        if (audioTrackIndex != VideoUtils.ERR_NO_TRACK_INDEX) {
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
            String audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
            int bitrate = AudioUtils.getAudioBitrate(audioFormat);
            int channelCount = AudioUtils.getChannelCount(audioFormat);
            int sampleRate = VideoUtils.getSampleRate(audioFormat);
            int maxBufferSize = AudioUtils.getAudioMaxBufferSize(audioFormat);

            MediaFormat audioEncodeFormat = MediaFormat.createAudioFormat(audioMimeType, sampleRate, channelCount);
            audioEncodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            audioEncodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);

            if (processorBuilder.mShouldChangedAudioSpeed) {
                if (processorBuilder.mStartTimeStamp != -1 || processorBuilder.mEndTimeStamp != -1 || processorBuilder.mSpeed != 0) {
                    long duration = audioFormat.getLong(MediaFormat.KEY_DURATION);
                    if (processorBuilder.mStartTimeStamp != -1 && processorBuilder.mEndTimeStamp != -1) {
                        duration = (processorBuilder.mEndTimeStamp - processorBuilder.mStartTimeStamp) * 1000;
                    }
                    if (processorBuilder.mSpeed != 0) {
                        duration /= processorBuilder.mSpeed;
                    }
                    audioEncodeFormat.setLong(MediaFormat.KEY_DURATION, duration);
                }
            } else {
                long videoDurationUs = inputDuration * 1000;
                long audioDurationUs = audioFormat.getLong(MediaFormat.KEY_DURATION);

                if (processorBuilder.mStartTimeStamp != -1 || processorBuilder.mEndTimeStamp != -1 || processorBuilder.mSpeed != 0) {
                    if (processorBuilder.mStartTimeStamp != -1 && processorBuilder.mEndTimeStamp != -1) {
                        videoDurationUs = (processorBuilder.mEndTimeStamp - processorBuilder.mStartTimeStamp) * 1000;
                    }
                    if (processorBuilder.mSpeed != 0) {
                        videoDurationUs /= processorBuilder.mSpeed;
                    }
                    long newDurationUs = videoDurationUs < audioDurationUs ? videoDurationUs : audioDurationUs;
                    audioEncodeFormat.setLong(MediaFormat.KEY_DURATION, newDurationUs);
                    audioEndTimeStamp = (processorBuilder.mStartTimeStamp == -1 ? 0 : processorBuilder.mStartTimeStamp) + (int)(newDurationUs / 1000);
                }
            }

            AudioUtils.checkCsd(audioFormat, MediaCodecInfo.CodecProfileLevel.AACObjectLC, sampleRate, channelCount);
            muxerAudioIndex = muxer.addTrack(audioEncodeFormat);
        }
        extractor.selectTrack(videoTrackIndex);

        int resultWidth = processorBuilder.mOutputWidth == 0 ? inputWidth : processorBuilder.mOutputWidth;
        int resultHeight = processorBuilder.mOutputHeight == 0 ? inputHeight : processorBuilder.mOutputHeight;
        resultWidth = resultWidth % 2 == 0 ? resultWidth : resultWidth + 1;
        resultHeight = resultHeight % 2 == 0 ? resultHeight : resultHeight + 1;
        if (inputRotation == 90 || inputRotation == 270) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
        }

        if (processorBuilder.mStartTimeStamp != -1) {
            extractor.seekTo(processorBuilder.mStartTimeStamp * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        } else {
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }

        AtomicBoolean decodeFinished = new AtomicBoolean(false);
        CountDownLatch muxerLatch = new CountDownLatch(1);
        VideoEncodeThread encodeThread = new VideoEncodeThread(extractor, muxer, processorBuilder.mBitrate,
                resultWidth, resultHeight, processorBuilder.mIFrameInterval, processorBuilder.mFrameRate,
                videoTrackIndex, decodeFinished, muxerLatch);

        int srcFrameRate = VideoUtils.getFrameRate(extractor);
        if (srcFrameRate == -1) {
            srcFrameRate = VideoUtils.getMeanFrameRate(extractor, videoTrackIndex);
            extractor.selectTrack(videoTrackIndex);
        }
        VideoDecodeThread decodeThread = new VideoDecodeThread(extractor, processorBuilder.mStartTimeStamp,
                processorBuilder.mEndTimeStamp, srcFrameRate, processorBuilder.mFrameRate, processorBuilder.mSpeed,
                processorBuilder.mShouldDropFrame, videoTrackIndex, decodeFinished, encodeThread);

        AudioProcessorThread audioProcessorThread = new AudioProcessorThread(processorBuilder.mContext, processorBuilder.mInputPath,
                muxer, processorBuilder.mStartTimeStamp, processorBuilder.mEndTimeStamp, processorBuilder.mShouldChangedAudioSpeed ? processorBuilder.mSpeed: 1,
                muxerAudioIndex, muxerLatch);


        decodeThread.start();
        encodeThread.start();
        audioProcessorThread.start();

        try {
            decodeThread.join();
            encodeThread.join();
            audioProcessorThread.join();
        } catch (Exception e) {
            LogUtils.w("Process Thread run failed, exception = " + e.getMessage());
        } finally {
            VideoUtils.closeExtractor(extractor);
            VideoUtils.closeMuxer(muxer);
        }


    }

    /**
     * 反转视频
     * @param inputPath
     * @param outputPath
     * @param shouldReverseAudio
     * @param listener
     */
    public void reverseVideo(String inputPath, String outputPath, boolean shouldReverseAudio, @NonNull IVideoReverseListener listener) {
        if (TextUtils.isEmpty(inputPath) || TextUtils.isEmpty(outputPath)) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_INPUT_OR_OUTPUT_PATH, VideoProcessException.ERR_INPUT_OR_OUTPUT_PATH));
            return;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputPath);
        } catch (Exception e) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_EXTRACTOR_SET_DATASOURCE, VideoProcessException.ERR_EXTRACTOR_SET_DATASOURCE));
            VideoUtils.closeExtractor(extractor);
            return;
        }
        int videoTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.VIDEO);
        if (videoTrackIndex == VideoUtils.ERR_NO_TRACK_INDEX) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_NO_VIDEO_TRACK, VideoProcessException.ERR_NO_VIDEO_TRACK));
            VideoUtils.closeExtractor(extractor);
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
        VideoUtils.closeExtractor(extractor);

        //如果视频中都是关键帧
        if (frameCount == keyFrameCount || frameCount == keyFrameCount + 1) {
            directReverseVideo(inputPath, outputPath, shouldReverseAudio, frameTimeStamps, listener);
        } else {



        }
    }

    //直接转换完全关键帧的视频
    private void directReverseVideo(String inputPath, String outputPath, boolean shouldReverseAudio, List<Long> frameTimeStamps, @NonNull IVideoReverseListener listener) {
        if (frameTimeStamps == null || frameTimeStamps.size() <= 0) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_INPUT_VIDEO_NO_KEYFRAME, VideoProcessException.ERR_INPUT_VIDEO_NO_KEYFRAME));
            return;
        }

        int duration = VideoUtils.getDuration(inputPath);
        if (duration == -1) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_INPUT_VIDEO_NO_DURATION, VideoProcessException.ERR_INPUT_VIDEO_NO_DURATION));
            return;
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(inputPath);
        } catch (Exception e) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_EXTRACTOR_SET_DATASOURCE, VideoProcessException.ERR_EXTRACTOR_SET_DATASOURCE));
            VideoUtils.closeExtractor(extractor);
            return;
        }
        int videoTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.VIDEO);
        int audioTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.AUDIO);

        //判断是否存在视频轨道
        if (videoTrackIndex == VideoUtils.ERR_NO_TRACK_INDEX) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_NO_VIDEO_TRACK, VideoProcessException.ERR_NO_VIDEO_TRACK));
            VideoUtils.closeExtractor(extractor);
            return;
        }

        //判断是否存在音频轨道
        if (audioTrackIndex == VideoUtils.ERR_NO_TRACK_INDEX) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_NO_AUDIO_TRACK, VideoProcessException.ERR_NO_AUDIO_TRACK));
            VideoUtils.closeExtractor(extractor);
            return;
        }

        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            listener.onVideoReverseFailed(new VideoProcessException(VideoProcessException.ERR_STR_MEDIAMUXER_CREATE_FAILED, VideoProcessException.ERR_MEDIAMUXER_CREATE_FAILED));
            VideoUtils.closeExtractor(extractor);
            VideoUtils.closeMuxer(muxer);
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

        VideoUtils.seekToLastFrame(extractor, videoTrackIndex, duration);
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
            videoProcessProgress *= VideoUtils.VIDEO_WEIGHT;
            listener.onVideoReverseProgress(videoProcessProgress);
        }


        //处理音频
        extractor.unselectTrack(videoTrackIndex);
        extractor.selectTrack(audioTrackIndex);

        //需要反转音频轨道
        if (shouldReverseAudio) {
            List<Long> audioFrameTimeStamps = VideoUtils.getFrameTimeStamps(extractor);
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
                audioProcessProgress = audioProcessProgress * VideoUtils.AUDIO_WEIGHT + VideoUtils.VIDEO_WEIGHT;
                listener.onVideoReverseProgress(audioProcessProgress);
            }
        } else {
            //不需要反转音频轨道
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (true) {
                long sampleTime = extractor.getSampleTime();
                if (sampleTime == -1) {
                    break;
                }
                bufferInfo.presentationTimeUs = sampleTime;
                bufferInfo.size = extractor.readSampleData(byteBuffer, 0);
                bufferInfo.flags = extractor.getSampleFlags();
                if (bufferInfo.size < 0) {
                    break;
                }
                muxer.writeSampleData(audioMuxerIndex, byteBuffer, bufferInfo);
                if (listener != null) {
                    float audioProcessProgress = bufferInfo.presentationTimeUs * 1.0f / audioDuration;
                    audioProcessProgress = (audioProcessProgress - 1.0f) > 0.00001 ? 1.0f : audioProcessProgress;
                    audioProcessProgress = audioProcessProgress * VideoUtils.AUDIO_WEIGHT + VideoUtils.VIDEO_WEIGHT;
                    listener.onVideoReverseProgress(audioProcessProgress);
                }
                extractor.advance();
            }
        }

        listener.onVideoReverseProgress(1.0f);
        VideoUtils.closeExtractor(extractor);
        VideoUtils.closeMuxer(muxer);
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
            VideoUtils.closeExtractor(inputVideoExtractor);
            return;
        }
        int srcInputAudioIndex = VideoUtils.getTrackIndex(inputVideoExtractor, TrackType.AUDIO);
        if (srcInputAudioIndex == -5) {
            LogUtils.w("addAudioFilter failed, input video has no audio track");
            VideoUtils.closeExtractor(inputVideoExtractor);
            return;
        }

        //2.抽取输入视频的视频轨道
        int srcInputVideoIndex = VideoUtils.getTrackIndex(inputVideoExtractor, TrackType.VIDEO);
        if (srcInputVideoIndex == -5) {
            LogUtils.w("addAudioFilter failed, input video has no video track");
            VideoUtils.closeExtractor(inputVideoExtractor);
            return;
        }

        //3.抽取输入音频的音频轨道
        MediaExtractor inputAudioExtractor = new MediaExtractor();
        try {
            inputAudioExtractor.setDataSource(params.mInputAudioPath);
        } catch (Exception e) {
            LogUtils.w("addAudioFilter failed, input audio path MediaExtractor failed");
            VideoUtils.closeExtractor(inputVideoExtractor);
            VideoUtils.closeExtractor(inputAudioExtractor);
            return;
        }
        int destInputAudioIndex = VideoUtils.getTrackIndex(inputAudioExtractor, TrackType.AUDIO);
        if (destInputAudioIndex == -5) {
            LogUtils.w("addAudioFilter failed, input audio has no audio track");
            VideoUtils.closeExtractor(inputVideoExtractor);
            VideoUtils.closeExtractor(inputAudioExtractor);
            return;
        }

        //4.开始合成视频
        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(params.mOutputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            VideoUtils.closeExtractor(inputVideoExtractor);
            VideoUtils.closeExtractor(inputAudioExtractor);
            VideoUtils.closeMuxer(mediaMuxer);
            return;
        }

        MediaFormat srcInputVideoFormat = VideoUtils.getFormat(inputVideoExtractor, srcInputVideoIndex);
        int rotation = VideoUtils.getRotation(inputVideoExtractor);
        mediaMuxer.setOrientationHint(rotation);
        int muxerVideoTrackIndex = mediaMuxer.addTrack(srcInputVideoFormat);

        //5.将视频中的音频取出来
        File cacheDir = new File(mContext.getCacheDir(), "audio");
        long timeStamp = System.currentTimeMillis();
        //srcInputAudioFile 将视频中分离的音频保存的位置
        File srcInputAudioFile = new File(cacheDir, "video_" + timeStamp + "_audio" + ".pcm");
        //inputAudioFile 输入的音频转化为pcm
        File inputAudioFile = new File(cacheDir, "audio_" + timeStamp + ".pcm");

        long inputVideoDuration = VideoUtils.getDuration(params.mInputVideoPath);
        if (inputVideoDuration == -1) {
            LogUtils.i("addAudioFilter failed, Input invalid video path");
            return;
        }
        long inputAudioDuration = VideoUtils.getDuration(params.mInputAudioPath);
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
