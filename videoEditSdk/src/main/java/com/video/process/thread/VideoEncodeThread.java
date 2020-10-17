package com.video.process.thread;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.video.process.utils.LogUtils;
import com.video.process.utils.VideoUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VideoEncodeThread extends Thread implements IVideoEncodeThread {

    private static final String TAG = "VideoEncodeThread";
    private static final int DEFAULT_FRAME_RATE = 20;
    private static final String OUTPUT_MIME_TYPE = "video/avc";
    private static final int TIMEOUT_USEC = 2500;

    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private AtomicBoolean mDecodeFinished;
    private CountDownLatch mMuxerLatch;
    private Exception mException;
    private int mBitrate;
    private int mResultWidth;
    private int mResultHeight;
    private int mIFrameInterval;
    private int mFrameRate;
    private MediaExtractor mExtractor;
    private int mVideoTrackIndex;
    //    private volatile InputSurface mInputSurface;
    private volatile CountDownLatch mEglContextLatch;
    private volatile Surface mSurface;

    public VideoEncodeThread(MediaExtractor extractor, MediaMuxer muxer,
                             int bitrate, int resultWidth, int resultHeight,
                             int iFrameInterval, int frameRate, int videoTrackIndex,
                             AtomicBoolean decodeFinished, CountDownLatch muxerLatch) {
        super("VideoEncodeThread");
        mExtractor = extractor;
        mMuxer = muxer;
        mBitrate = bitrate;
        mResultWidth = resultWidth;
        mResultHeight = resultHeight;
        mIFrameInterval = iFrameInterval;
        mFrameRate = frameRate;
        mVideoTrackIndex = videoTrackIndex;
        mDecodeFinished = decodeFinished;
        mMuxerLatch = muxerLatch;
        mEglContextLatch = new CountDownLatch(1);
    }

    @Override
    public void run() {
        super.run();

        try {
            doEncodeVideo();
        } catch (Exception e) {
            mException = e;
        } finally {
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
            }
        }
    }

    private void doEncodeVideo() throws IOException {
        MediaFormat inputFormat = mExtractor.getTrackFormat(mVideoTrackIndex);
        //初始化编码器
        int frameRate;
        if (mFrameRate > 0) {
            frameRate = mFrameRate;
        } else {
            frameRate = inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE)
                    ? inputFormat.getInteger(inputFormat.KEY_FRAME_RATE)
                    : DEFAULT_FRAME_RATE;
        }
        String mimeType = OUTPUT_MIME_TYPE;
        MediaFormat outputFormat = MediaFormat.createVideoFormat(mimeType, mResultWidth, mResultHeight);
        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);

        mEncoder = MediaCodec.createEncoderByType(mimeType);
        boolean supportProfileHigh = VideoUtils.trySetProfileAndLevel(
                mEncoder, mimeType, outputFormat,
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        if (supportProfileHigh) {
            LogUtils.i(TAG, "supportProfileHigh,enable ProfileHigh");
        }
        int maxBitrate = VideoUtils.getMaxSupportBitrate(mEncoder, mimeType);
        if (maxBitrate > 0 && mBitrate > maxBitrate) {
            LogUtils.e(TAG, mBitrate + " bitrate too large,set to:" + maxBitrate);
            mBitrate = (int)(maxBitrate * 0.8f); //直接设置最大值小米2报错
        }
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        mEncoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mSurface = mEncoder.createInputSurface();

        mEncoder.start();
        mEglContextLatch.countDown();

        boolean signalEncodeEnd = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int encodeTryAgainCount = 0;
        int videoTrackIndex = -5;
        boolean detectTimeError = false;
        final int VIDEO_FRAME_TIME_US = (int)(1000 * 1000f / frameRate);
        long lastVideoFrameTimeUs = -1;
        //开始编码
        //输出
        while (true) {
            if (mDecodeFinished.get() && !signalEncodeEnd) {
                signalEncodeEnd = true;
                mEncoder.signalEndOfInputStream();
            }
            int outputBufferIndex = mEncoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
            LogUtils.i(TAG, "encode outputBufferIndex = " + outputBufferIndex);
            if (signalEncodeEnd &&
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                encodeTryAgainCount++;
                if (encodeTryAgainCount > 10) {
                    //三星S8上出现signalEndOfInputStream之后一直tryAgain的问题
                    LogUtils.e(TAG, "INFO_TRY_AGAIN_LATER 10 times,force End!");
                    break;
                }
            } else {
                encodeTryAgainCount = 0;
            }
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue;
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mEncoder.getOutputFormat();
                if (videoTrackIndex == -5) {
                    videoTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                    mMuxerLatch.countDown();
                }
                LogUtils.i(TAG, "encode newFormat = " + newFormat);
            } else if (outputBufferIndex < 0) {
                // ignore
                LogUtils.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: " +
                        outputBufferIndex);
            } else {
                //编码数据可用
                ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferIndex);
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM &&
                        info.presentationTimeUs < 0) {
                    info.presentationTimeUs = 0;
                }
                //写入视频
                if (!detectTimeError && lastVideoFrameTimeUs != -1 &&
                        info.presentationTimeUs <
                                lastVideoFrameTimeUs + VIDEO_FRAME_TIME_US / 2) {
                    //某些视频帧时间会出错
                    LogUtils.e(TAG, "video 时间戳错误，lastVideoFrameTimeUs:" +
                            lastVideoFrameTimeUs + " "
                            + "info.presentationTimeUs:" + info.presentationTimeUs +
                            " VIDEO_FRAME_TIME_US:" + VIDEO_FRAME_TIME_US);
                    detectTimeError = true;
                }
                if (detectTimeError) {
                    info.presentationTimeUs = lastVideoFrameTimeUs + VIDEO_FRAME_TIME_US;
                    LogUtils.e(TAG, "video 时间戳错误，使用修正的时间戳:" + info.presentationTimeUs);
                    detectTimeError = false;
                }
                if (info.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    lastVideoFrameTimeUs = info.presentationTimeUs;
                }
                LogUtils.i(TAG, "writeSampleData,size:" + info.size +
                        " time:" + info.presentationTimeUs / 1000);
                mMuxer.writeSampleData(videoTrackIndex, outputBuffer, info);
                mEncoder.releaseOutputBuffer(outputBufferIndex, false);
                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    LogUtils.i(TAG, "encoderDone");
                    break;
                }
            }
        }
    }

    @Override
    public Surface getSurface() {
        return mSurface;
    }

    @Override
    public CountDownLatch getEglContextLatch() {
        return mEglContextLatch;
    }
}
