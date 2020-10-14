package com.video.process.listener;

import com.video.process.exception.VideoProcessException;

public interface IVideoProcessListener {

    void onVideoReverseProgress(float progress);
    void onVideoReverseFailed(VideoProcessException e);

}
