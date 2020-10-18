package com.video.process.utils;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.video.process.model.TrackType;
import com.video.process.model.VideoSize;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;

public class VideoUtils {

    private static final String TAG = "VideoUtils";

    public static final int DEFAULT_I_FRAME_INTERVAL = 1;
    public static final float VIDEO_WEIGHT = 0.8f;
    public static final float AUDIO_WEIGHT = (1 - VIDEO_WEIGHT);
    public static final int ERR_NO_TRACK_INDEX = -5;

    public static int getDuration(@NonNull String inputPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int duration = -1;
        try {
            retriever.setDataSource(inputPath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            duration = TextUtils.isEmpty(durationStr) ? -1 : Integer.parseInt(durationStr);
        } catch (Exception e) {
            return -1;
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
        return ERR_NO_TRACK_INDEX;
    }

    public static int getRotation(MediaExtractor extractor) {
        int videoTrackIndex = getTrackIndex(extractor, TrackType.VIDEO);
        MediaFormat videoFormat = extractor.getTrackFormat(videoTrackIndex);
        return videoFormat.containsKey(MediaFormat.KEY_ROTATION) ? videoFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
    }

    public static MediaFormat getFormat(MediaExtractor extractor, int trackIndex) {
        return extractor.getTrackFormat(trackIndex);
    }

    //找到最后一个关键帧
    public static void seekToLastFrame(MediaExtractor extractor, int trackIndex, int duration) {
        int seekToDuration = duration * 1000;
        if (extractor.getSampleTrackIndex() != trackIndex) {
            extractor.selectTrack(trackIndex);
        }
        extractor.seekTo(seekToDuration, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        while (seekToDuration > 0 &&
                extractor.getSampleTrackIndex() != trackIndex) {
            seekToDuration -= 10 * 1000;
            extractor.seekTo(seekToDuration, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
    }

    //获取特定轨道的帧数
    public static List<Long> getFrameTimeStamps(MediaExtractor extractor) {
        List<Long> frameTimeStamps = new ArrayList<>();
        while(true) {
            long sampleTime = extractor.getSampleTime();
            if (sampleTime < 0) {
                break;
            }
            frameTimeStamps.add(sampleTime);
            extractor.advance();
        }
        return frameTimeStamps;
    }

    public static int getSampleRate(MediaFormat format) {
        return format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }

    public static boolean trySetProfileAndLevel(MediaCodec codec, String mime,
                                                MediaFormat format,
                                                int profile, int level) {
        MediaCodecInfo codecInfo = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mime);
        MediaCodecInfo.CodecProfileLevel[] profileLevels = capabilities.profileLevels;
        if (profileLevels == null) {
            return false;
        }
        for (MediaCodecInfo.CodecProfileLevel itemLevel : profileLevels) {
            if (itemLevel.profile == profile) {
                if (itemLevel.level == level) {
                    format.setInteger(MediaFormat.KEY_PROFILE, profile);
                    format.setInteger(MediaFormat.KEY_LEVEL, level);
                    return true;
                }
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int getMaxSupportBitrate(MediaCodec codec, String mime) {
        try {
            MediaCodecInfo codecInfo = codec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mime);
            Integer maxBitrate = capabilities.getVideoCapabilities().getBitrateRange().getUpper();
            return maxBitrate;
        } catch (Exception e) {
            LogUtils.e(TAG, e.getMessage());
            return -1;
        }
    }

    public static int getFrameRate(MediaExtractor extractor) {
        MediaFormat videoFormat = extractor.getTrackFormat(TrackType.VIDEO);
        if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            return videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        }
        return -1;
    }

    public static int getMeanFrameRate(MediaExtractor extractor, int videoTrackIndex) {
        extractor.selectTrack(videoTrackIndex);
        long lastFrameTimeUs = 0;
        int frameCount = 0;
        while (true) {
            long sampleTime = extractor.getSampleTime();
            if (sampleTime < 0) {
                break;
            } else {
                lastFrameTimeUs = sampleTime;
            }
            frameCount++;
            extractor.advance();
        }
        extractor.unselectTrack(videoTrackIndex);

        return (int)(frameCount * 1.0f / lastFrameTimeUs / 1000 / 1000);
    }

    public static void closeExtractor(MediaExtractor extractor) {
        if (extractor != null) {
            extractor.release();
        }
    }

    public static void closeMuxer(MediaMuxer muxer) {
        if (muxer != null) {
            muxer.stop();
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
