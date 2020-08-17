package com.video.edit.ext;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileInputStream;
import java.io.InputStream;

public class FileUtils {
    public static Bitmap decodeFile(String file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            return decodeInputStream(fis);
        } catch (Exception e) {

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {

                }
            }
        }
        return null;
    }

    public static Bitmap decodeInputStream(InputStream is) {
        BitmapFactory.Options opt_decord = new BitmapFactory.Options();
        opt_decord.inPurgeable = true;
        opt_decord.inInputShareable = true;
        Bitmap bitmap_ret = null;
        try {
            bitmap_ret = BitmapFactory.decodeStream(is, null, opt_decord);
        } catch (Exception e) {
            bitmap_ret = null;
        }
        return bitmap_ret;
    }
}
