package com.video.compose;

public class VideoSize {
    public int mWidth;
    public int mHeight;

    public VideoSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoSize videoSize = (VideoSize) o;
        return mWidth == videoSize.mWidth &&
                mHeight == videoSize.mHeight;
    }

}
