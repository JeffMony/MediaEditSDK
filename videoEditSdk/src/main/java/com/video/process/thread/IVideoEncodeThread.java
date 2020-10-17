package com.video.process.thread;

import android.view.Surface;

import java.util.concurrent.CountDownLatch;

public interface IVideoEncodeThread {
    Surface getSurface();
    CountDownLatch getEglContextLatch();
}
