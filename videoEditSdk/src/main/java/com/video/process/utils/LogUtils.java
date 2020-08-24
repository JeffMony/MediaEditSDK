package com.video.process.utils;

import android.util.Log;

public class LogUtils {
    private static final String TAG = "VideoEdit";
    private static final boolean VERBOSE = false;
    private static final boolean DEBUG = false;
    private static final boolean INFO = true;
    private static final boolean WARIN = true;
    private static final boolean ERROR = true;

    public static void v(String msg) {
        if (VERBOSE) {
            Log.v(TAG, msg);
        }
    }

    public static void d(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    public static void i(String msg) {
        if (INFO) {
            Log.i(TAG, msg);
        }
    }

    public static void w(String msg) {
        if (WARIN) {
            Log.w(TAG, msg);
        }
    }

    public static void e(String msg) {
        if (ERROR) {
            Log.e(TAG, msg);
        }
    }
}
