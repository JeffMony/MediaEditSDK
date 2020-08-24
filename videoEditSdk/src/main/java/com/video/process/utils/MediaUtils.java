package com.video.process.utils;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.video.process.model.TrackType;
import com.video.process.model.VideoSize;

import java.io.Closeable;
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

    public static int getTrackIndex(MediaExtractor extractor, int type) {
        int trackCount = extractor.getTrackCount();
        for(int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            LogUtils.i("type = " + type + ", format=" + format);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (type == TrackType.AUDIO && mime.startsWith("audio/")) {
                return i;
            }
            if (type == TrackType.VIDEO && mime.startsWith("video/")) {
                return i;
            }
        }
        return -5;
    }

    public static int getRotation(MediaExtractor extractor) {
        int videoTrackIndex = getTrackIndex(extractor, TrackType.VIDEO);
        MediaFormat videoFormat = extractor.getTrackFormat(videoTrackIndex);
        return videoFormat.containsKey(MediaFormat.KEY_ROTATION) ? videoFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
    }

    public static MediaFormat getFormat(MediaExtractor extractor, int trackIndex) {
        return extractor.getTrackFormat(trackIndex);
    }

    public static int getAudioMaxBufferSize(@NonNull MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            return format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        } else {
            return 100 * 1000;
        }
    }

    public static void closeExtractor(MediaExtractor extractor) {
        if (extractor != null) {
            extractor.release();
        }
    }

    public static void closeMuxer(MediaMuxer muxer) {
        if (muxer != null) {
            muxer.release();
        }
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
            }
        }
    }

}
