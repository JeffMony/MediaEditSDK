package com.video.process;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.video.process.model.TrackType;
import com.video.process.utils.LogUtils;
import com.video.process.utils.VideoUtils;

import java.nio.ByteBuffer;

public class AudioProcessorManager {
    private static AudioProcessorManager sInstance = null;

    private AudioProcessorManager() {}

    public static AudioProcessorManager getInstance() {
        if (sInstance == null) {
            synchronized (AudioProcessorManager.class) {
                if (sInstance == null) {
                    sInstance = new AudioProcessorManager();
                }
            }
        }
        return sInstance;
    }

    public void decodeToPCM(String inputPath, String outputPath, long start, long end) {
        MediaExtractor inputExtractor = new MediaExtractor();
        try {
            inputExtractor.setDataSource(inputPath);
        } catch (Exception e) {
            LogUtils.w("decodeToPCM failed, input path is invalid");
            return;
        }
        int audioTrackIndex = VideoUtils.getTrackIndex(inputExtractor, TrackType.AUDIO);
        if (audioTrackIndex == -5) {
            LogUtils.w("decodeToPCM failed, input path has no audio track");
            VideoUtils.closeExtractor(inputExtractor);
            return;
        }
        inputExtractor.selectTrack(audioTrackIndex);
        //seek 到 start附近的一个关键帧
        inputExtractor.seekTo(start, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        MediaFormat audioFormat = inputExtractor.getTrackFormat(audioTrackIndex);

        MediaCodec decoder = null;
        try {
            decoder = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        } catch (Exception e) {
            LogUtils.w("decodeToPCM failed, create decode type failed, mime type = " + audioFormat.getString(MediaFormat.KEY_MIME));
            VideoUtils.closeExtractor(inputExtractor);
            return;
        }
        decoder.configure(audioFormat, null, null, 0);
        decoder.start();

        int maxBufferSize;
        if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            maxBufferSize = 100 * 1000;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        //从原始视频文件中解码出音频,然后编码到输出的音频文件中
        boolean decodeDone = false;
        boolean decodeInputDone = false;
//        File pcmFile = new File()
    }

    /**
     *
     * @param inputVideoPath
     * @param inputAudioPath
     * @param outputVideoPath
     * @param repeat 当inputAudioPath不够长时是否重复填充
     */
    public void replaceAudioTrack(String inputVideoPath, String inputAudioPath, String outputVideoPath, boolean repeat) {
        LogUtils.i("replaceAudioTrack " + inputVideoPath +", " + inputAudioPath +", " + outputVideoPath);
        MediaExtractor inputVideoExtractor = new MediaExtractor();
        try {
            inputVideoExtractor.setDataSource(inputVideoPath);
        } catch (Exception e) {
            LogUtils.w("replaceAudioTrack failed, input video path is invaild");
            VideoUtils.closeExtractor(inputVideoExtractor);
            return;
        }
        MediaExtractor inputAudioExtractor = new MediaExtractor();
        try {
            inputAudioExtractor.setDataSource(inputAudioPath);
        } catch (Exception e) {
            LogUtils.w("replaceAudioTrack failed, input audio path is invaild");
            VideoUtils.closeExtractor(inputVideoExtractor);
            VideoUtils.closeExtractor(inputAudioExtractor);
            return;
        }
        int inputVideoTrack = VideoUtils.getTrackIndex(inputVideoExtractor, TrackType.VIDEO);
        int inputAudioTrack = VideoUtils.getTrackIndex(inputAudioExtractor, TrackType.AUDIO);
        LogUtils.i("inputVideoTrack="+inputVideoTrack+", inputAudioTrack="+inputAudioTrack);
        MediaFormat inputVideoFormat = inputVideoExtractor.getTrackFormat(inputVideoTrack);
        MediaFormat inputAudioFormat = inputAudioExtractor.getTrackFormat(inputAudioTrack);
        inputAudioFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        LogUtils.i("inputVideoFormat = " + inputVideoFormat);
        LogUtils.i("inputAudioFormat = " + inputAudioFormat);
        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            LogUtils.w("replaceAudioTrack failed, mediaMuxer create failed");
            VideoUtils.closeExtractor(inputVideoExtractor);
            VideoUtils.closeExtractor(inputAudioExtractor);
            return;
        }
        int muxerVideoTrack = mediaMuxer.addTrack(inputVideoFormat);
        int muxerAudioTrack = mediaMuxer.addTrack(inputAudioFormat);
        mediaMuxer.start();

        inputVideoExtractor.selectTrack(inputVideoTrack);

        //写视频轨道
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int maxBufferSize = inputVideoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer videoBuffer = ByteBuffer.allocateDirect(maxBufferSize);
        long lastVideoTimeUs = 0;
        while(true) {
            int size = inputVideoExtractor.readSampleData(videoBuffer, 0);
            if (size == -1) {
                break;
            }
            long sampleTimeUs = inputVideoExtractor.getSampleTime();
            int flags = inputVideoExtractor.getSampleFlags();
            bufferInfo.presentationTimeUs = sampleTimeUs;
            bufferInfo.flags = flags;
            bufferInfo.size = size;
            mediaMuxer.writeSampleData(muxerVideoTrack, videoBuffer, bufferInfo);
            lastVideoTimeUs = sampleTimeUs;
            inputVideoExtractor.advance();
        }

        inputAudioExtractor.selectTrack(inputAudioTrack);

        //写音频轨道
        int sampleRate = inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int aacFrameTimeUs = 1024 * 1000 * 1000 / sampleRate;
        maxBufferSize = VideoUtils.getAudioMaxBufferSize(inputAudioFormat);
        ByteBuffer audioBuffer = ByteBuffer.allocateDirect(maxBufferSize);
        long lastAudioTimeUs = 0;
        long baseAudioTimeUs = 0;
        while(lastAudioTimeUs < lastVideoTimeUs) {
            inputAudioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while(true) {
                int size = inputAudioExtractor.readSampleData(audioBuffer, 0);
                if (size == -1) {
                    break;
                }
                long sampleTimeUs = inputAudioExtractor.getSampleTime();
                sampleTimeUs += baseAudioTimeUs;
                if (sampleTimeUs > lastVideoTimeUs) {
                    lastAudioTimeUs = sampleTimeUs;
                    break;
                }
                int flags = inputAudioExtractor.getSampleFlags();
                bufferInfo.presentationTimeUs = sampleTimeUs;
                bufferInfo.flags = flags;
                bufferInfo.size = size;
                mediaMuxer.writeSampleData(muxerAudioTrack, audioBuffer, bufferInfo);
                lastAudioTimeUs = sampleTimeUs;
                inputAudioExtractor.advance();
            }
            baseAudioTimeUs = lastAudioTimeUs + aacFrameTimeUs;
            if (!repeat) {
                break;
            }
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
        }
    }
}
