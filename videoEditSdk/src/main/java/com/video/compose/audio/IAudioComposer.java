package com.video.compose.audio;

/**
 * Created by sudamasayuki2 on 2018/02/24.
 */

public interface IAudioComposer {

    void setup();

    boolean stepPipeline();

    long getWrittenPresentationTimeUs();

    boolean isFinished();

    void release();
}
