package com.video.process.compose.video;

import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import com.video.process.compose.ComposeParams;
import com.video.process.compose.VideoSize;
import com.video.process.utils.LogUtils;
import com.video.process.utils.WorkThreadHandler;
import com.video.process.preview.filter.GlFilter;
import com.video.process.compose.FillMode;
import com.video.process.compose.Rotation;
import com.video.process.compose.VideoCustomException;
import com.video.process.compose.filter.IResolutionFilter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;

public class Mp4Composer {

    private final static String TAG = Mp4Composer.class.getSimpleName();

    private ComposeParams mComposeParams;
    private VideoComposeListener mComposeListener;

    public Mp4Composer(@NonNull ComposeParams params) {
        mComposeParams = params;
    }

    public Mp4Composer listener(@NonNull VideoComposeListener listener) {
        this.mComposeListener = listener;
        return this;
    }

    public Mp4Composer start() {
        WorkThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                File outputFile = new File(mComposeParams.mDestPath);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                final File srcFile = new File(mComposeParams.mSrcPath);
                final FileInputStream fileInputStream;
                try {
                    fileInputStream = new FileInputStream(srcFile);
                } catch (Exception e) {
                    VideoCustomException exception = new VideoCustomException(VideoCustomException.SRC_VIDEO_FILE_ERROR, e);
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(exception);
                    }
                    return;
                }
                if (fileInputStream == null) {
                    VideoCustomException exception = new VideoCustomException(VideoCustomException.SRC_VIDEO_FILE_ERROR2, new Throwable());
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(exception);
                    }
                    return;
                }
                FileDescriptor fd;
                try {
                    fd = fileInputStream.getFD();
                } catch (Exception e) {
                    VideoCustomException exception = new VideoCustomException(VideoCustomException.SRC_VIDEO_FILE_ERROR3, e);
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(exception);
                    }
                    return;
                }
                final int videoRotate = getVideoRotation(fd);
                final VideoSize srcVideoSize = getVideoSize(fd);
                mComposeParams.setSrcVideoSize(srcVideoSize);

                Mp4ComposerEngine engine = new Mp4ComposerEngine();
                engine.setProgressCallback(new Mp4ComposerEngine.ComposeProgressCallback() {
                    @Override
                    public void onProgress(final double progress) {
                        if (mComposeListener != null) {
                            mComposeListener.onProgress(progress);
                        }
                    }

                    @Override
                    public void onCompleted() {
                        if (mComposeListener != null) {
                            mComposeListener.onCompleted();
                        }
                    }

                    @Override
                    public void onFailed(VideoCustomException e) {
                        if (mComposeListener != null) {
                            mComposeListener.onFailed(e);
                        }
                    }
                });
                engine.setDataSource(fd);
                if (mComposeParams.mFilter == null) {
                    mComposeParams.setFilter(new GlFilter());
                }

                if (mComposeParams.mFillMode == null) {
                    mComposeParams.setFillMode(FillMode.PRESERVE_ASPECT_FIT);
                }

                if (mComposeParams.mCustomFillMode != null) {
                    mComposeParams.setFillMode(FillMode.CUSTOM);
                }

                if (mComposeParams.mDestVideoSize == null) {
                    if (mComposeParams.mFillMode == FillMode.CUSTOM) {
                        mComposeParams.setDestVideoSize(srcVideoSize);
                    } else {
                        Rotation rotate = Rotation.fromInt(mComposeParams.mRotateDegree + videoRotate);
                        if (rotate == Rotation.ROTATION_90 || rotate == Rotation.ROTATION_270) {
                            mComposeParams.setDestVideoSize(new VideoSize(srcVideoSize.mHeight, srcVideoSize.mWidth));
                        } else {
                            mComposeParams.setDestVideoSize(srcVideoSize);
                        }
                        mComposeParams.setRotateDegree(rotate.getRotation());
                    }
                }
                if (mComposeParams.mFilter instanceof IResolutionFilter) {
                    ((IResolutionFilter) mComposeParams.mFilter).setResolution(mComposeParams.mDestVideoSize);
                }

                if (mComposeParams.mTimeScale < 2) {
                    mComposeParams.setTimeScale(1);
                }

                LogUtils.d(TAG + ", rotation = " + (mComposeParams.mRotateDegree + videoRotate));
                LogUtils.d(TAG + ", inputResolution width = " + srcVideoSize.mWidth + " height = " + srcVideoSize.mHeight);
                LogUtils.d(TAG + ", outputResolution width = " + mComposeParams.mDestVideoSize.mWidth + " height = " + mComposeParams.mDestVideoSize.mHeight);
                LogUtils.d(TAG + ", fillMode = " + mComposeParams.mFillMode);

                try {
                    if (mComposeParams.mBitRate < 0) {
                        mComposeParams.setBitRate(calcBitRate(mComposeParams.mDestVideoSize.mWidth, mComposeParams.mDestVideoSize.mHeight));
                    }
                    engine.compose(mComposeParams);

                } catch (VideoCustomException e) {
                    e.printStackTrace();
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(e);
                    }
                    return;
                }

                if (mComposeListener != null) {
                    mComposeListener.onCompleted();
                }
            }
        });

        return this;
    }

    public interface VideoComposeListener {

        void onProgress(double progress);

        void onCompleted();

        void onFailed(VideoCustomException e);

        void onCanceled();
    }

    private int getVideoRotation(FileDescriptor fd) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fd);
        String orientation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        retriever.release();
        return Integer.valueOf(orientation);
    }

    private int calcBitRate(int width, int height) {
        final int bitrate = (int) (0.25 * 30 * width * height);
        LogUtils.i(TAG + ", bitrate=" + bitrate);
        return bitrate;
    }

    private VideoSize getVideoSize(FileDescriptor fd) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fd);
        int width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        retriever.release();
        return new VideoSize(width, height);
    }

}
