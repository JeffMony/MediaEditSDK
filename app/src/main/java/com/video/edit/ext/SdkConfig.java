package com.video.edit.ext;

import android.content.Context;

import java.io.File;

public class SdkConfig {
    public static final int SPEED_RANGE = 30;
    public static final String DEFAULT_TEMP_VIDEO_LOCATION = "/storage/emulated/0/movies/process.mp4";

    public static int MSG_UPDATE = 1;
    public static boolean USE_EXOPLAYER = true;

    // 对于长视频, 每隔3s截取一个缩略图
    public static int MAX_FRAME_INTERVAL_MS = 3 * 1000;

    // 默认显示10个缩略图
    public static int DEFAULT_FRAME_COUNT = 10;

    // 裁剪最小时间为3s
    public static int minSelection = 3000; // 最短3s

    // 裁剪最长时间为30s
    public static long maxSelection = 30000; // 最长30s

    public static File getVideoDir(Context context) {
        return new File(context.getExternalFilesDir("Video"), "EditDir");
    }

    public static final String FILTER_DIALOG = "filter_dialog";
    public static final String MUSIC_DIALOG = "music_dialog";
}
