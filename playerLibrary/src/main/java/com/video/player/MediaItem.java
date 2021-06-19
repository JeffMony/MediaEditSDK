package com.video.player;

public class MediaItem {
    private String mTitle;
    private String mPath;
    private long mDuration;
    private long mSize;

    public MediaItem(String title, String path, long duration, long size) {
        mTitle = title;
        mPath = path;
        mDuration = duration;
        mSize = size;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getPath() {
        return mPath;
    }

    public long getDuration() {
        return mDuration;
    }

    public long getSize() {
        return mSize;
    }
}
