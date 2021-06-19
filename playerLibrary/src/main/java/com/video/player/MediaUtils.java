package com.video.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

public class MediaUtils {
    public static MediaItem getMediaItem(ContentResolver resolver, Intent intent) {
        Uri uri = intent.getData();
        MediaItem mediaItem = null;
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.TITLE));
                String path = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA));
                long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.DURATION));
                long size = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media.SIZE));
                mediaItem = new MediaItem(title, path, duration, size);
            }
            cursor.close();
        }
        return mediaItem;
    }

    public static long getDuration(Context context, String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, Uri.parse(path));
            String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Long.parseLong(duration);
        } catch (Exception e) {

        } finally {
            if (mmr != null) {
                mmr.release();
            }
        }

        return 0;
    }
}
