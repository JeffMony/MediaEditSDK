package com.video.process.listener;

import com.video.process.exception.VideoProcessException;

public interface IVideoReverseListener {

    void onVideoReverseProgress(float progress);
    void onVideoReverseFailed(VideoProcessException e);

}
