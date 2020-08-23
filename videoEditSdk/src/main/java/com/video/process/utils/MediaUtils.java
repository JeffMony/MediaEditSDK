package com.video.process.utils;

import android.media.MediaMetadataRetriever;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.video.process.model.VideoSize;

import java.io.FileDescriptor;

public class MediaUtils {

    public static long getDuration(@NonNull String inputPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        long duration = -1L;
        try {
            retriever.setDataSource(inputPath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = TextUtils.isEmpty(durationStr) ? -1L : Long.parseLong(durationStr);
        } catch (Exception e) {
            return -1L;
        } finally {
            if (retriever != null) {
                retriever.release();
            }
        }
        return duration;
    }

    public static VideoSize getVideoSize(FileDescriptor fd) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int width;
        int height;
        try {
            retriever.setDataSource(fd);
            width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (Exception e) {
            return null;
        } finally {
            if (retriever != null) {
                retriever.release();
            }
        }
        return new VideoSize(width, height);
    }


}
