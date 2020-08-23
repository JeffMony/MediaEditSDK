package com.video.process.model;

public class VideoRange {

    public long mStart;
    public long mEnd;

    public VideoRange(long start, long end) {
        mStart = start;
        mEnd = end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoRange that = (VideoRange) o;
        return mStart == that.mStart &&
                mEnd == that.mEnd;
    }
}
