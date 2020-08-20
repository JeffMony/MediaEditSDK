package com.video.process.surface;

public class MagicJni {
    static {
        System.loadLibrary("MagicBeautify");
    }

    public static native void glReadPixels(int x, int y, int width, int height, int format, int type);
}
