package com.video.compose;

import androidx.annotation.NonNull;

import com.video.egl.GlFilterList;
import com.video.epf.filter.GlFilter;

public class ComposeParams {
    public String mSrcPath;         //源文件路径
    public String mDestPath;        //生成文件的路径
    public VideoSize mDestVideoSize; //生成视频文件大小
    public VideoSize mSrcVideoSize;  //源视频文件大小
    public VideoRange mVideoRange;  //裁减的文件时间范围
    public int mBitRate;            //视频的码率
    public int mFrameRate;          //视频的帧率
    public boolean mIsMute;         //是否静音
    public boolean mFlipVertical;   //垂直翻转
    public boolean mFlipHorizontal; //水平翻转
    public int mRotateDegree;       //旋转角度
    public int mTimeScale;          //裁减的视频间隔
    public FillMode mFillMode;
    public CustomFillMode mCustomFillMode;
    public GlFilter mFilter;
    public GlFilterList mFilterList;

    public ComposeParams(@NonNull String srcPath, @NonNull String destPath) {
        mSrcPath = srcPath;
        mDestPath = destPath;
    }

    public void setDestVideoSize(VideoSize size) {
        mDestVideoSize = size;
    }

    public void setSrcVideoSize(VideoSize size) { mSrcVideoSize = size; }

    public void setVideoRange(VideoRange range) {
        mVideoRange = range;
    }

    public void setBitRate(int bitRate) {
        mBitRate = bitRate;
    }

    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public void setIsMute(boolean mute) {
        mIsMute = mute;
    }

    public void setFlipVertical(boolean flipVertical) {
        mFlipVertical = flipVertical;
    }

    public void setFlipHorizontal(boolean flipHorizontal) {
        mFlipHorizontal = flipHorizontal;
    }

    public void setRotateDegree(int degree) {
        mRotateDegree = degree;
    }

    public void setTimeScale(int scale) {
        mTimeScale = scale;
    }

    public void setFillMode(FillMode mode) {
        mFillMode = mode;
    }

    public void setCustomFillMode(CustomFillMode mode) {
        mCustomFillMode = mode;
        mFillMode = FillMode.CUSTOM;
    }

    public void setFilter(GlFilter filter) {
        mFilter = filter;
    }

    public void setFilterList(GlFilterList filterList) {
        mFilterList = filterList;
    }

}
