package com.video.edit;

import android.app.Application;

import com.video.edit.ext.SdkConfig;

import java.io.File;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        File sdkDir = SdkConfig.getVideoDir(this);
        if (!sdkDir.exists()) {
            sdkDir.mkdir();
        }
    }
}