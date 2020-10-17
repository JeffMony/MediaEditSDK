package com.video.process.utils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.jeffmony.soundtouch.SoundTouch;
import com.video.process.compose.audio.Pcm2Wav;
import com.video.process.model.TrackType;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class AudioUtils {

    private static final String TAG = "AudioUtils";

    private final static Map<Integer, Integer> sFreqIdxMap =
            new HashMap<Integer, Integer>();

    static {
        sFreqIdxMap.put(96000, 0);
        sFreqIdxMap.put(88200, 1);
        sFreqIdxMap.put(64000, 2);
        sFreqIdxMap.put(48000, 3);
        sFreqIdxMap.put(44100, 4);
        sFreqIdxMap.put(32000, 5);
        sFreqIdxMap.put(24000, 6);
        sFreqIdxMap.put(22050, 7);
        sFreqIdxMap.put(16000, 8);
        sFreqIdxMap.put(12000, 9);
        sFreqIdxMap.put(11025, 10);
        sFreqIdxMap.put(8000, 11);
        sFreqIdxMap.put(7350, 12);
    }

    public static final int DEFAULT_MAX_BUFFER_SIZE = 100 * 1024;
    public static final int DEFAULT_CHANNEL_COUNT = 1;
    public static final int DEFAULT_AAC_BITRATE = 192 * 1000;

    public static int getAudioMaxBufferSize(@NonNull MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            return format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            return DEFAULT_MAX_BUFFER_SIZE;
        }
    }

    public static int getAudioBitrate(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
            return format.getInteger(MediaFormat.KEY_BIT_RATE);
        } else {
            return DEFAULT_AAC_BITRATE;
        }
    }

    public static int getChannelCount(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            return format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } else {
            return DEFAULT_CHANNEL_COUNT;
        }
    }

    public static void checkCsd(MediaFormat audioFormat, int profile, int sampleRate, int channel) {
        int freqIdx = sFreqIdxMap.containsKey(sampleRate) ? sFreqIdxMap.get(sampleRate) : 4;
        ByteBuffer csd = ByteBuffer.allocate(2);
        csd.put(0, (byte) (profile << 3 | freqIdx >> 1));
        csd.put(1, (byte)((freqIdx & 0x01) << 7 | channel << 3));
        audioFormat.setByteBuffer("csd-0", csd);
    }

    /**
     * 不需要改变音频速率的情况下，直接读写就可
     * 还需要解决进度问题
     */
    public static long writeAudioTrack(MediaExtractor extractor, MediaMuxer mediaMuxer,
                    int muxerAudioIndex, int startTimeUs,
                    int endTimeUs, long baseMuxerFrameTimeUs)  {
        int audioTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.AUDIO);
        extractor.selectTrack(audioTrackIndex);
        extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
        long audioDurationUs = audioFormat.getLong(MediaFormat.KEY_DURATION);
        int maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        long lastFrametimeUs = baseMuxerFrameTimeUs;
        while (true) {
            long sampleTimeUs = extractor.getSampleTime();
            if (sampleTimeUs == -1) {
                break;
            }
            if (sampleTimeUs < startTimeUs) {
                extractor.advance();
                continue;
            }
            if (sampleTimeUs > endTimeUs) {
                break;
            }
            bufferInfo.presentationTimeUs =
                    sampleTimeUs - startTimeUs + baseMuxerFrameTimeUs;
            bufferInfo.flags = extractor.getSampleFlags();
            bufferInfo.size = extractor.readSampleData(byteBuffer, 0);
            if (bufferInfo.size < 0) {
                break;
            }
            LogUtils.i(TAG, "writeAudioSampleData,time:" + bufferInfo.presentationTimeUs / 1000f);
            mediaMuxer.writeSampleData(muxerAudioIndex, byteBuffer, bufferInfo);
            lastFrametimeUs = bufferInfo.presentationTimeUs;
            extractor.advance();
        }
        return lastFrametimeUs;
    }

    /**
     * 需要改变音频速率的情况下，需要先解码->改变速率->编码
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void writeAudioTrackDecode(
            Context context, MediaExtractor extractor, MediaMuxer mediaMuxer,
            int muxerAudioIndex, Integer startTimeUs, Integer endTimeUs,
            @NonNull Float speed)
            throws Exception {
        int audioTrackIndex = VideoUtils.getTrackIndex(extractor, TrackType.AUDIO);
        extractor.selectTrack(audioTrackIndex);
        extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
        long durationUs = audioFormat.getLong(MediaFormat.KEY_DURATION);
        int maxBufferSize = getAudioMaxBufferSize(audioFormat);

        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        //调整音频速率需要重解码音频帧
        MediaCodec decoder = MediaCodec.createDecoderByType(
                audioFormat.getString(MediaFormat.KEY_MIME));
        decoder.configure(audioFormat, null, null, 0);
        decoder.start();

        boolean decodeDone = false;
        boolean encodeDone = false;
        boolean decodeInputDone = false;
        final int TIMEOUT_US = 2500;
        File pcmFile = new File(context.getCacheDir(), System.currentTimeMillis() + ".pcm");
        FileChannel writeChannel = new FileOutputStream(pcmFile).getChannel();
        try {
            while (!decodeDone) {
                if (!decodeInputDone) {
                    boolean eof = false;
                    int decodeInputIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (decodeInputIndex >= 0) {
                        long sampleTimeUs = extractor.getSampleTime();
                        if (sampleTimeUs == -1) {
                            eof = true;
                        } else if (sampleTimeUs < startTimeUs) {
                            extractor.advance();
                            continue;
                        } else if (endTimeUs != null && sampleTimeUs > endTimeUs) {
                            eof = true;
                        }

                        if (eof) {
                            decodeInputDone = true;
                            decoder.queueInputBuffer(decodeInputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            info.size = extractor.readSampleData(buffer, 0);
                            info.presentationTimeUs = sampleTimeUs;
                            info.flags = extractor.getSampleFlags();
                            ByteBuffer inputBuffer = decoder.getInputBuffer(decodeInputIndex);
                            inputBuffer.put(buffer);
                            LogUtils.i(TAG, "audio decode queueInputBuffer " +
                                    info.presentationTimeUs / 1000);
                            decoder.queueInputBuffer(decodeInputIndex, 0, info.size,
                                    info.presentationTimeUs, info.flags);
                            extractor.advance();
                        }
                    }
                }

                while (!decodeDone) {
                    int outputBufferIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    } else if (outputBufferIndex ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = decoder.getOutputFormat();
                        LogUtils.i(TAG, "audio decode newFormat = " + newFormat);
                    } else if (outputBufferIndex < 0) {
                        // ignore
                        LogUtils.e(TAG, "unexpected result from audio decoder.dequeueOutputBuffer: " +
                                outputBufferIndex);
                    } else {
                        if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                            decodeDone = true;
                        } else {
                            ByteBuffer decodeOutputBuffer =
                                    decoder.getOutputBuffer(outputBufferIndex);
                            LogUtils.i(TAG, "audio decode saveFrame " + info.presentationTimeUs / 1000);
                            writeChannel.write(decodeOutputBuffer);
                        }
                        decoder.releaseOutputBuffer(outputBufferIndex, false);
                    }
                }
            }
        } finally {
            writeChannel.close();
            extractor.release();
            decoder.release();
        }

        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int oriChannelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        File wavFile = new File(context.getCacheDir(), pcmFile.getName() + ".wav");

        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        if (oriChannelCount == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }
        new Pcm2Wav(sampleRate, channelConfig, oriChannelCount,
                AudioFormat.ENCODING_PCM_16BIT)
                .doPcm2Wav(pcmFile.getAbsolutePath(), wavFile.getAbsolutePath());
        //开始处理pcm
        LogUtils.i(TAG,"start process pcm speed");
        File outFile =
                new File(context.getCacheDir(), pcmFile.getName() + ".outpcm");
        SoundTouch soundTouch = new SoundTouch();
        soundTouch.setTempo(speed);

        int res = soundTouch.processFile(wavFile.getAbsolutePath(), outFile.getAbsolutePath());
        if (res < 0) {
            pcmFile.delete();
            wavFile.delete();
            outFile.delete();
            return;
        }
        //重新将速率变化过后的pcm写入
        MediaExtractor pcmExtrator = new MediaExtractor();
        pcmExtrator.setDataSource(outFile.getAbsolutePath());
        audioTrackIndex = VideoUtils.getTrackIndex(pcmExtrator, TrackType.AUDIO);
        pcmExtrator.selectTrack(audioTrackIndex);
        MediaFormat pcmTrackFormat = pcmExtrator.getTrackFormat(audioTrackIndex);
        maxBufferSize = getAudioMaxBufferSize(pcmTrackFormat);
        durationUs = pcmTrackFormat.getLong(MediaFormat.KEY_DURATION);
        buffer = ByteBuffer.allocateDirect(maxBufferSize);

        String audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
        int bitrate = getAudioBitrate(audioFormat);
        int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        MediaFormat encodeFormat = MediaFormat.createAudioFormat(
                audioMimeType, sampleRate,
                channelCount); //参数对应-> mime type、采样率、声道数
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate); //比特率
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize);
        MediaCodec encoder = MediaCodec.createEncoderByType(audioMimeType);
        encoder.configure(encodeFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        boolean encodeInputDone = false;
        long lastAudioFrameTimeUs = -1;
        final int AAC_FRAME_TIME_US = 1024 * 1000 * 1000 / sampleRate;
        boolean detectTimeError = false;
        try {
            while (!encodeDone) {
                int inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
                if (!encodeInputDone && inputBufferIndex >= 0) {
                    long sampleTime = pcmExtrator.getSampleTime();
                    if (sampleTime < 0) {
                        encodeInputDone = true;
                        encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        int flags = pcmExtrator.getSampleFlags();
                        buffer.clear();
                        int size = pcmExtrator.readSampleData(buffer, 0);
                        ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                        inputBuffer.clear();
                        inputBuffer.put(buffer);
                        inputBuffer.position(0);
                        LogUtils.i(TAG,"audio queuePcmBuffer " + sampleTime / 1000 + " size:" + size);
                        encoder.queueInputBuffer(inputBufferIndex, 0, size, sampleTime,
                                flags);
                        pcmExtrator.advance();
                    }
                }

                while (true) {
                    int outputBufferIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    } else if (outputBufferIndex ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        LogUtils.i(TAG,"audio decode newFormat = " + newFormat);
                    } else if (outputBufferIndex < 0) {
                        // ignore
                        LogUtils.e(TAG,"unexpected result from audio decoder.dequeueOutputBuffer: " +
                                outputBufferIndex);
                    } else {
                        if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                            encodeDone = true;
                            break;
                        }
                        ByteBuffer encodeOutputBuffer =
                                encoder.getOutputBuffer(outputBufferIndex);
                        LogUtils.i(TAG,"audio writeSampleData " + info.presentationTimeUs +
                                " size:" + info.size + " flags:" + info.flags);
                        if (!detectTimeError && lastAudioFrameTimeUs != -1 &&
                                info.presentationTimeUs <
                                        lastAudioFrameTimeUs + AAC_FRAME_TIME_US) {
                            //某些情况下帧时间会出错，目前未找到原因（系统相机录得双声道视频正常，我录的单声道视频不正常）
                            LogUtils.e(TAG,"audio 时间戳错误，lastAudioFrameTimeUs:" +
                                    lastAudioFrameTimeUs + " "
                                    + "info.presentationTimeUs:" + info.presentationTimeUs);
                            detectTimeError = true;
                        }
                        if (detectTimeError) {
                            info.presentationTimeUs =
                                    lastAudioFrameTimeUs + AAC_FRAME_TIME_US;
                            LogUtils.e(TAG,"audio 时间戳错误，使用修正的时间戳:" +
                                    info.presentationTimeUs);
                            detectTimeError = false;
                        }
                        if (info.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                            lastAudioFrameTimeUs = info.presentationTimeUs;
                        }
                        mediaMuxer.writeSampleData(muxerAudioIndex, encodeOutputBuffer,
                                info);
                        encodeOutputBuffer.clear();
                        encoder.releaseOutputBuffer(outputBufferIndex, false);
                    }
                }
            }
        } finally {
            pcmFile.delete();
            wavFile.delete();
            outFile.delete();
            pcmExtrator.release();
            encoder.release();
        }
    }
}
