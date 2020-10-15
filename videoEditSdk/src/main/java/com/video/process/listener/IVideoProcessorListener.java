package com.video.process.listener;

import com.video.process.exception.VideoProcessException;

public interface IVideoProcessorListener {

    void onVideoProcessorProgress(float progress);

    void onVideoProcessorFailed(VideoProcessException e);
}
