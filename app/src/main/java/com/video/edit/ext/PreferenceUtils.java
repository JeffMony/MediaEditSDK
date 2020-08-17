package com.video.edit.ext;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtils {
    private static final String NAME = "litedo";

    public static void putInt(Context context, String key, int value) {
        if (context == null) return;
        SharedPreferences preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(key, value);
        editor.commit();
    }

    public static int getInt(Context context, String key, int defVal) {
        if (context == null) return defVal;
        SharedPreferences preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        return preferences.getInt(key, defVal);
    }
}
