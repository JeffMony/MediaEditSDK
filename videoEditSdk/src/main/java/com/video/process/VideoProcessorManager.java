package com.video.process;

import androidx.annotation.NonNull;

import com.video.process.model.ProcessParams;

public class VideoProcessorManager {

    private static VideoProcessorManager sInstance = null;

    private VideoProcessorManager() { }

    public static VideoProcessorManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoProcessorManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoProcessorManager();
                }
            }
        }
        return sInstance;
    }

    public void addAudioFilter(@NonNull ProcessParams params) {

    }

}
