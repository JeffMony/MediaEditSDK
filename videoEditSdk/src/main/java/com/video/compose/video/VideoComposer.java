
package com.video.compose.video;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.video.compose.ComposeParams;
import com.video.compose.Rotation;
import com.video.egl.DecoderOutputSurface;
import com.video.egl.EncoderSurface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java
// Refer: https://github.com/ypresto/android-transcoder/blob/master/lib/src/main/java/net/ypresto/androidtranscoder/engine/VideoTrackTranscoder.java
public class VideoComposer {
    private static final String TAG = "VComposer";
    private static final int DRAIN_STATE_NONE = 0;
    private static final int DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1;
    private static final int DRAIN_STATE_CONSUMED = 2;

    private final MediaExtractor mMediaExtractor;
    private final int mTrackIndex;
    private final MediaFormat mOutputFormat;
    private final MuxRender mMuxRender;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec mDecoder;
    private MediaCodec mEncoder;
    private ByteBuffer[] mDecoderInputBuffers;
    private ByteBuffer[] mEncoderOutputBuffers;
    private MediaFormat mActualOutputFormat;
    private DecoderOutputSurface mDecoderSurface;
    private EncoderSurface mEncoderSurface;
    private boolean mIsExtractorEOS;
    private boolean mIsDecoderEOS;
    private boolean mIsEncoderEOS;
    private boolean mDecoderStarted;
    private boolean mEncoderStarted;
    private long mWrittenPresentationTimeUs;
    private final int mTimeScale;

    public VideoComposer(MediaExtractor mediaExtractor, int trackIndex,
                  MediaFormat outputFormat, MuxRender muxRender, int timeScale) {
        this.mMediaExtractor = mediaExtractor;
        this.mTrackIndex = trackIndex;
        this.mOutputFormat = outputFormat;
        this.mMuxRender = muxRender;
        this.mTimeScale = timeScale;
    }

    public void setUp(ComposeParams composeParams) {
        mMediaExtractor.selectTrack(mTrackIndex);
        try {
            mEncoder = MediaCodec.createEncoderByType(mOutputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        mEncoder.configure(mOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoderSurface = new EncoderSurface(mEncoder.createInputSurface());
        mEncoderSurface.makeCurrent();
        mEncoder.start();
        mEncoderStarted = true;
        mEncoderOutputBuffers = mEncoder.getOutputBuffers();

        MediaFormat inputFormat = mMediaExtractor.getTrackFormat(mTrackIndex);
        if (inputFormat.containsKey("rotation-degrees")) {
            // Decoded video is rotated automatically in Android 5.0 lollipop.
            // Turn off here because we don't want to encode rotated one.
            // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
            inputFormat.setInteger("rotation-degrees", 0);
        }

        mDecoderSurface = new DecoderOutputSurface(composeParams.mFilter, composeParams.mFilterList);
        mDecoderSurface.setRotation(Rotation.fromInt(composeParams.mRotateDegree));
        mDecoderSurface.setOutputVideoSize(composeParams.mDestVideoSize);
        mDecoderSurface.setInputResolution(composeParams.mSrcVideoSize);
        mDecoderSurface.setFillMode(composeParams.mFillMode);
        mDecoderSurface.setFillModeCustomItem(composeParams.mCustomFillMode);
        mDecoderSurface.setFlipHorizontal(composeParams.mFlipHorizontal);
        mDecoderSurface.setFlipVertical(composeParams.mFlipVertical);
        mDecoderSurface.setupAll();
        try {
            mDecoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        mDecoder.configure(inputFormat, mDecoderSurface.getSurface(), null, 0);
        mDecoder.start();
        mDecoderStarted = true;
        mDecoderInputBuffers = mDecoder.getInputBuffers();
    }


    public boolean stepPipeline() {
        boolean busy = false;

        int status;
        while (drainEncoder() != DRAIN_STATE_NONE) {
            busy = true;
        }
        do {
            status = drainDecoder();
            if (status != DRAIN_STATE_NONE) {
                busy = true;
            }
            // NOTE: not repeating to keep from deadlock when encoder is full.
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY);
        while (drainExtractor() != DRAIN_STATE_NONE) {
            busy = true;
        }

        return busy;
    }

    long getWrittenPresentationTimeUs() {
        return mWrittenPresentationTimeUs;
    }

    boolean isFinished() {
        return mIsEncoderEOS;
    }

    void release() {
        if (mDecoderSurface != null) {
            mDecoderSurface.release();
            mDecoderSurface = null;
        }
        if (mEncoderSurface != null) {
            mEncoderSurface.release();
            mEncoderSurface = null;
        }
        if (mDecoder != null) {
            if (mDecoderStarted) mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
        if (mEncoder != null) {
            if (mEncoderStarted) mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }

    private int drainExtractor() {
        Log.d(TAG, "drainExtractor(): isExtractorEOS:"+mIsExtractorEOS);
        if (mIsExtractorEOS) return DRAIN_STATE_NONE;
        int trackIndex = mMediaExtractor.getSampleTrackIndex();
        Log.d(TAG, "drainExtractor(): trackIndex:"+trackIndex+", this.trackIndex:"+this.mTrackIndex);
        if (trackIndex >= 0 && trackIndex != this.mTrackIndex) {
            return DRAIN_STATE_NONE;
        }
        int result = mDecoder.dequeueInputBuffer(0);
        Log.d(TAG, "drainExtractor(): decoder.dequeueInputBuffer result:" + result);
        if (result < 0) return DRAIN_STATE_NONE;
        if (trackIndex < 0) {
            mIsExtractorEOS = true;
            mDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return DRAIN_STATE_NONE;
        }
        int sampleSize = mMediaExtractor.readSampleData(mDecoderInputBuffers[result], 0);
        boolean isKeyFrame = (mMediaExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;

        long sampleTime = mMediaExtractor.getSampleTime();
        Log.d(TAG, "drainExtractor(): sampleTime:"+sampleTime +", endTimeMs:" + mEnd);
        if (sampleTime > mEnd * 1000) {
            Log.e(TAG, "drainExtractor(): sampleTime:"+sampleTime+", reach the end time");
            mIsExtractorEOS = true;
            mMediaExtractor.unselectTrack(this.mTrackIndex);
            mDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return DRAIN_STATE_NONE;
        }

        mDecoder.queueInputBuffer(result, 0, sampleSize, sampleTime / mTimeScale, isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0);
        mMediaExtractor.advance();
        return DRAIN_STATE_CONSUMED;
    }

    private int drainDecoder() {
        if (mIsDecoderEOS) return DRAIN_STATE_NONE;
        int result = mDecoder.dequeueOutputBuffer(mBufferInfo, 0);
        Log.d(TAG+".drainDecoder", "drainDecoder: dequeueOutputBuffer, return:"+result);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG+".drainDecoder", "drainDecoder: end of stream! bufferInfo.offset:"+mBufferInfo.offset+", size:"+mBufferInfo.size+",presentationTimeUs:"+mBufferInfo.presentationTimeUs);
            mEncoder.signalEndOfInputStream();
            mIsDecoderEOS = true;
            mBufferInfo.size = 0;
        }

        Log.d(TAG+".drainDecoder", "drainDecoder: bufferInfo.presentationTimeUs:"+mBufferInfo.presentationTimeUs +", endTimeMs:"+ mEnd);
        if (mBufferInfo.presentationTimeUs > mEnd * 1000) {
            Log.w(TAG+".drainDecoder", "drainDecoder: reach the clip end ms! bufferInfo.offset:"+mBufferInfo.offset+", size:"+mBufferInfo.size+",presentationTimeUs:"+mBufferInfo.presentationTimeUs);
            mEncoder.signalEndOfInputStream();
            mIsDecoderEOS = true;
            mBufferInfo.flags = mBufferInfo.flags | MediaCodec.BUFFER_FLAG_END_OF_STREAM;
        }

        boolean doRender = (mBufferInfo.size > 0);

        // NOTE: doRender will block if buffer (of encoder) is full.
        // Refer: http://bigflake.com/mediacodec/CameraToMpegTest.java.txt
        mDecoder.releaseOutputBuffer(result, doRender);
        if (doRender) {
            mDecoderSurface.awaitNewImage();
            mDecoderSurface.drawImage(mBufferInfo.presentationTimeUs* 1000);
            mEncoderSurface.setPresentationTime(mBufferInfo.presentationTimeUs * 1000);
            mEncoderSurface.swapBuffers();
        }
        return DRAIN_STATE_CONSUMED;
    }

    private int drainEncoder() {
        if (mIsEncoderEOS) return DRAIN_STATE_NONE;
        int result = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
        Log.d(TAG+".drainEncoder", "drainEncoder: dequeueOutputBuffer() return:"+result);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                if (mActualOutputFormat != null) {
                    throw new RuntimeException("Video output format changed twice.");
                }
                mActualOutputFormat = mEncoder.getOutputFormat();
                mMuxRender.setOutputFormat(MuxRender.SampleType.VIDEO, mActualOutputFormat);
                mMuxRender.onSetOutputFormat();
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                mEncoderOutputBuffers = mEncoder.getOutputBuffers();
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        if (mActualOutputFormat == null) {
            throw new RuntimeException("Could not determine actual output format.");
        }

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(TAG+".drainEncoder", "drainEncoder: reach the end@!");
            mIsEncoderEOS = true;
            mBufferInfo.set(0, 0, 0, mBufferInfo.flags);
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // SPS or PPS, which should be passed by MediaFormat.
            mEncoder.releaseOutputBuffer(result, false);
            return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        Log.d(TAG+".drainEncoder", "drainEncoder: writeSampleData time:"+mBufferInfo.presentationTimeUs);
        mMuxRender.writeSampleData(MuxRender.SampleType.VIDEO, mEncoderOutputBuffers[result], mBufferInfo);
        mWrittenPresentationTimeUs = mBufferInfo.presentationTimeUs;
        mEncoder.releaseOutputBuffer(result, false);
        return DRAIN_STATE_CONSUMED;
    }

    private long mStart, mEnd;

    public void setClipRange(long startTimeMs, long endTimeMs) {
        this.mStart = startTimeMs;
        this.mEnd = endTimeMs;
        //跳到指定的位置
        mMediaExtractor.seekTo(mStart, SEEK_TO_PREVIOUS_SYNC);
    }
}
