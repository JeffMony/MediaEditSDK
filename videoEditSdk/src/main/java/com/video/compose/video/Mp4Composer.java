package com.video.compose.video;

import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import com.video.compose.VideoSize;
import com.video.compose.utils.LogUtils;
import com.video.epf.filter.GlFilter;
import com.video.compose.FillMode;
import com.video.egl.GlFilterList;
import com.video.compose.CustomFillMode;
import com.video.compose.Rotation;
import com.video.compose.VideoCustomException;
import com.video.compose.filter.IResolutionFilter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Mp4Composer {

    private final static String TAG = Mp4Composer.class.getSimpleName();

    private final String srcPath;
    private final String destPath;
    private GlFilter filter;
    private GlFilterList filterList;
    private VideoSize mVideoSize;
    private int bitrate = -1;
    private int frameRate = 30;
    private boolean mute = false;
    private Rotation rotation = Rotation.NORMAL;
    private VideoComposeListener mComposeListener;
    private FillMode fillMode = FillMode.PRESERVE_ASPECT_FIT;
    private CustomFillMode fillModeCustomItem;
    private int timeScale = 1;
    private float resolutionScale = 1f;
    private long clipStartMs, clipEndMs;
    private boolean flipVertical = false;
    private boolean flipHorizontal = false;

    private ExecutorService executorService;

    public Mp4Composer(@NonNull final String srcPath, @NonNull final String destPath) {
        this.srcPath = srcPath;
        this.destPath = destPath;
    }

    public Mp4Composer filter(@NonNull GlFilter filter) {
        this.filter = filter;
        return this;
    }

    public Mp4Composer filterList(@NonNull GlFilterList filterList) {
        this.filterList = filterList;
        LogUtils.d(TAG + ", set filterList = " + this.filterList);
        return this;
    }

    public Mp4Composer size(int width, int height) {
        this.mVideoSize = new VideoSize(width, height);
        return this;
    }

    public Mp4Composer clip(long start, long end) {
        this.clipStartMs = start;
        this.clipEndMs = end;
        return this;
    }

    public Mp4Composer videoBitrate(int bitrate) {
        this.bitrate = bitrate;
        return this;
    }

    public Mp4Composer mute(boolean mute) {
        this.mute = mute;
        return this;
    }

    public Mp4Composer frameRate(int value) {
        this.frameRate = value;
        return this;
    }

    public Mp4Composer flipVertical(boolean flipVertical) {
        this.flipVertical = flipVertical;
        return this;
    }

    public Mp4Composer flipHorizontal(boolean flipHorizontal) {
        this.flipHorizontal = flipHorizontal;
        return this;
    }

    public Mp4Composer rotation(@NonNull Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public Mp4Composer fillMode(@NonNull FillMode fillMode) {
        this.fillMode = fillMode;
        return this;
    }

    public Mp4Composer customFillMode(@NonNull CustomFillMode fillModeCustomItem) {
        this.fillModeCustomItem = fillModeCustomItem;
        this.fillMode = FillMode.CUSTOM;
        return this;
    }

    public Mp4Composer listener(@NonNull VideoComposeListener listener) {
        this.mComposeListener = listener;
        return this;
    }

    public Mp4Composer timeScale(final int timeScale) {
        this.timeScale = timeScale;
        return this;
    }

    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }


    public Mp4Composer start() {
        getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                File outputFile = new File(destPath);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                final File srcFile = new File(srcPath);
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
                final VideoSize srcVideoResolution = getVideoResolution(fd);

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
                if (filter == null) {
                    filter = new GlFilter();
                }

                if (fillMode == null) {
                    fillMode = FillMode.PRESERVE_ASPECT_FIT;
                }

                if (fillModeCustomItem != null) {
                    fillMode = FillMode.CUSTOM;
                }

                if (mVideoSize == null) {
                    if (fillMode == FillMode.CUSTOM) {
                        mVideoSize = srcVideoResolution;
                    } else {
                        Rotation rotate = Rotation.fromInt(rotation.getRotation() + videoRotate);
                        if (rotate == Rotation.ROTATION_90 || rotate == Rotation.ROTATION_270) {
                            mVideoSize = new VideoSize(srcVideoResolution.mHeight, srcVideoResolution.mWidth);
                        } else {
                            mVideoSize = srcVideoResolution;
                        }
                    }
                }
                if (filter instanceof IResolutionFilter) {
                    ((IResolutionFilter) filter).setResolution(mVideoSize);
                }

                if (timeScale < 2) {
                    timeScale = 1;
                }

                LogUtils.d(TAG + ", filterList = " + filterList);
                LogUtils.d(TAG + ", rotation = " + (rotation.getRotation() + videoRotate));
                LogUtils.d(TAG + ", inputResolution width = " + srcVideoResolution.mWidth + " height = " + srcVideoResolution.mHeight);
                mVideoSize = new VideoSize((int) (mVideoSize.mWidth * resolutionScale), (int) (mVideoSize.mHeight * resolutionScale));
                LogUtils.d(TAG + ", outputResolution width = " + mVideoSize.mWidth + " height = " + mVideoSize.mHeight);
                LogUtils.d(TAG + ", fillMode = " + fillMode);

                try {
                    if (bitrate < 0) {
                        bitrate = calcBitRate(mVideoSize.mWidth, mVideoSize.mHeight);
                    }
                    engine.compose(
                            destPath,
                            mVideoSize,
                            filter,
                            filterList,
                            bitrate,
                            frameRate,
                            mute,
                            Rotation.fromInt(rotation.getRotation() + videoRotate),
                            srcVideoResolution,
                            fillMode,
                            fillModeCustomItem,
                            timeScale,
                            flipVertical,
                            flipHorizontal,
                            clipStartMs,
                            clipEndMs
                    );

                } catch (VideoCustomException e) {
                    e.printStackTrace();
                    if (mComposeListener != null) {
                        mComposeListener.onFailed(e);
                    }
                    executorService.shutdown();
                    return;
                }

                if (mComposeListener != null) {
                    mComposeListener.onCompleted();
                }
                executorService.shutdown();
            }
        });

        return this;
    }

    public void cancel() {
        getExecutorService().shutdownNow();
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

    private VideoSize getVideoResolution(FileDescriptor fd) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fd);
        int width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        retriever.release();
        return new VideoSize(width, height);
    }

}
