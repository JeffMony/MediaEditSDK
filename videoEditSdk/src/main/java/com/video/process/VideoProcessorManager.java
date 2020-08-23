package com.video.process;

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

    
}
