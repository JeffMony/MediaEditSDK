package com.video.process.thread;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.video.process.surface.InputSurface;
import com.video.process.surface.OutputSurface;
import com.video.process.utils.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VideoDecodeThread extends Thread {
    private static final String TAG = "VideoDecodeThread";

    private static final int TIMEOUT_USEC = 2500;

    private MediaExtractor mExtractor;
    private int mStartTime;
    private int mEndTime;
    private int mSrcFrameRate;
    private int mDestFrameRate;
    private float mSpeed;
    private boolean mShouldDropFrame;
    private int mVideoTrackIndex;
    private AtomicBoolean mDecodeFinished;
    private IVideoEncodeThread mVideoEncodeThread;

    private InputSurface mInputSurface;
    private OutputSurface mOutputSurface;

    private Exception mException;
    private MediaCodec mDecoder;

    public VideoDecodeThread(MediaExtractor extractor,
                             int startTime, int endTime,
                             int srcFrameRate, int destFrameRate,
                             float speed, boolean shouldDropFrame,
                             int videoTrackIndex,
                             AtomicBoolean decodeFinished,
                             IVideoEncodeThread videoEncodeThread) {
        super("VideoDecodeThread");
        mExtractor = extractor;
        mStartTime = startTime;
        mEndTime = endTime;
        mSrcFrameRate = srcFrameRate;
        mDestFrameRate = destFrameRate;
        mSpeed = speed;
        mShouldDropFrame = shouldDropFrame;
        mVideoTrackIndex = videoTrackIndex;
        mDecodeFinished = decodeFinished;
        mVideoEncodeThread = videoEncodeThread;
    }

    @Override
    public void run() {
        super.run();

        try {
            doVideoDecode();
        } catch (Exception e) {
            mException = e;
        } finally {
            if (mInputSurface != null) {
                mInputSurface.release();
            }
            if (mOutputSurface != null) {
                mOutputSurface.release();
            }

            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
            }
        }

    }

    private void doVideoDecode() throws IOException {
        CountDownLatch eglContextLatch = mVideoEncodeThread.getEglContextLatch();
        try {
            boolean await = eglContextLatch.await(5, TimeUnit.SECONDS);
            if (await) {
                mException = new TimeoutException("wait eglContext timeout!");
                return;
            }
        } catch (Exception e) {
            mException = e;
            return;
        }
        Surface encodeSurface = mVideoEncodeThread.getSurface();
        mInputSurface = new InputSurface(encodeSurface);
        mInputSurface.makeCurrent();

        MediaFormat videoFormat = mExtractor.getTrackFormat(mVideoTrackIndex);
        mDecoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
        mOutputSurface = new OutputSurface();
        mDecoder.configure(videoFormat, mOutputSurface.getSurface(), null, 0);
        mDecoder.start();

        //丢帧判断
        int frameIntervalForDrop = 0;
        int dropCount = 0;
        int frameIndex = 1;
        if (mShouldDropFrame && mSrcFrameRate != 0 && mDestFrameRate != 0) {
            if (mSpeed != 0) {
                mSrcFrameRate = (int)(mSrcFrameRate * mSpeed);
            }
            if (mSrcFrameRate > mDestFrameRate) {
                frameIntervalForDrop = mDestFrameRate / (mSrcFrameRate - mDestFrameRate);
                frameIntervalForDrop = frameIntervalForDrop == 0 ? 1 : frameIntervalForDrop;
                dropCount = (mSrcFrameRate - mDestFrameRate) / mDestFrameRate;
                dropCount = dropCount == 0 ? 1 : dropCount;
                LogUtils.w(TAG,"帧率过高，需要丢帧:" + mSrcFrameRate + "->" + mDestFrameRate +
                        " frameIntervalForDrop:" + frameIntervalForDrop +
                        " dropCount:" + dropCount);
            }
        }
        //开始解码
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean decoderDone = false;
        boolean inputDone = false;
        long videoStartTimeUs = -1;
        int decodeTryAgainCount = 0;

        while (!decoderDone) {
            //还有帧数据，输入解码器
            if (!inputDone) {
                boolean eof = false;
                int index = mExtractor.getSampleTrackIndex();
                if (index == mVideoTrackIndex) {
                    int inputBufIndex = mDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuf = mDecoder.getInputBuffer(inputBufIndex);
                        int chunkSize = mExtractor.readSampleData(inputBuf, 0);
                        if (chunkSize < 0) {
                            mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            decoderDone = true;
                        } else {
                            long sampleTime = mExtractor.getSampleTime();
                            mDecoder.queueInputBuffer(inputBufIndex, 0, chunkSize, sampleTime,
                                    0);
                            mExtractor.advance();
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }

                if (eof) {
                    //解码输入结束
                    int inputBufIndex = mDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (inputBufIndex >= 0) {
                        mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    }
                }
            }
            boolean decoderOutputAvailable = !decoderDone;
            if (decoderDone) {
                LogUtils.i(TAG, "decoderOutputAvailable:" + decoderOutputAvailable);
            }
            while (decoderOutputAvailable) {
                int outputBufferIndex =
                        mDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                LogUtils.i(TAG, "outputBufferIndex = " + outputBufferIndex);
                if (inputDone && outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    decodeTryAgainCount++;
                    if (decodeTryAgainCount > 10) {
                        //小米2上出现BUFFER_FLAG_END_OF_STREAM之后一直tryAgain的问题
                        LogUtils.i(TAG, "INFO_TRY_AGAIN_LATER 10 times,force End!");
                        decoderDone = true;
                        break;
                    }
                } else {
                    decodeTryAgainCount = 0;
                }
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break;
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mDecoder.getOutputFormat();
                    LogUtils.i(TAG, "decode newFormat = " + newFormat);
                } else if (outputBufferIndex < 0) {
                    // ignore
                    LogUtils.e(TAG, "unexpected result from decoder.dequeueOutputBuffer: " +
                            outputBufferIndex);
                } else {
                    boolean doRender = true;
                    //解码数据可用
                    if (mEndTime != -1 &&
                            bufferInfo.presentationTimeUs >= mEndTime * 1000) {
                        inputDone = true;
                        decoderDone = true;
                        doRender = false;
                        bufferInfo.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    }
                    if (mStartTime != -1 &&
                            bufferInfo.presentationTimeUs < mStartTime * 1000) {
                        doRender = false;
                        LogUtils.e(TAG, "drop frame startTime = " + mStartTime +
                                " present time = " + bufferInfo.presentationTimeUs / 1000);
                    }
                    if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        decoderDone = true;
                        mDecoder.releaseOutputBuffer(outputBufferIndex, false);
                        LogUtils.i(TAG, "decoderDone");
                        break;
                    }
                    //检查是否需要丢帧
                    if (frameIntervalForDrop > 0) {
                        int remainder = frameIndex % (frameIntervalForDrop + dropCount);
                        if (remainder > frameIntervalForDrop || remainder == 0) {
                            LogUtils.w(TAG, "帧率过高，丢帧:" + frameIndex);
                            doRender = false;
                        }
                    }
                    frameIndex++;
                    mDecoder.releaseOutputBuffer(outputBufferIndex, doRender);
                    if (doRender) {
                        boolean errorWait = false;
                        try {
                            mOutputSurface.awaitNewImage();
                        } catch (Exception e) {
                            errorWait = true;
                            LogUtils.w(TAG, e.getMessage());
                        }
                        if (!errorWait) {
                            if (videoStartTimeUs == -1) {
                                videoStartTimeUs = bufferInfo.presentationTimeUs;
                                LogUtils.i(TAG, "videoStartTime:" + videoStartTimeUs / 1000);
                            }
                            mOutputSurface.drawImage(false);
                            long presentationTimeNs =
                                    (bufferInfo.presentationTimeUs - videoStartTimeUs) * 1000;
                            if (mSpeed != 0) {
                                presentationTimeNs /= mSpeed;
                            }
                            LogUtils.i(TAG, "drawImage,setPresentationTimeMs:" +
                                    presentationTimeNs / 1000 / 1000);
                            mInputSurface.setPresentationTime(presentationTimeNs);
                            mInputSurface.swapBuffers();
                            break;
                        }
                    }
                }
            }
        }
        mDecodeFinished.set(true);
    }
}
