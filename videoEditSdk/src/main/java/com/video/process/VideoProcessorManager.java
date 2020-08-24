package com.video.process;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import androidx.annotation.NonNull;

import com.video.process.model.ProcessParams;
import com.video.process.model.TrackType;
import com.video.process.model.VideoRange;
import com.video.process.utils.LogUtils;
import com.video.process.utils.MediaUtils;
import com.video.process.utils.VideoCustomException;
import com.video.process.utils.WorkThreadHandler;

import java.io.File;
import java.util.concurrent.CountDownLatch;

public class VideoProcessorManager {

    private static VideoProcessorManager sInstance = null;
    private Context mContext;

    private VideoProcessorManager() { }

    public static VideoProcessorManager getInstance() {
        if (sInstance == null) {
            synchronized (VideoProcessorManager.class) {
                if (sInstance == null) {
                    sInstance = new VideoProcessorManager();
                }
            }
        }
        return sInstance;
    }

    public void initContext(@NonNull Context context) {
        mContext = context;
    }

    public void replaceAudioTrack(@NonNull final ProcessParams params) {
        WorkThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {
                AudioProcessorManager.getInstance().replaceAudioTrack(params.mInputVideoPath, params.mInputAudioPath, params.mOutputVideoPath, true);
            }
        });
    }

    public void addAudioFilter(@NonNull ProcessParams params) {
        //1.抽取输入视频的音频轨道
        MediaExtractor inputVideoExtractor = new MediaExtractor();
        try {
            inputVideoExtractor.setDataSource(params.mInputVideoPath);
        } catch (Exception e) {
            LogUtils.w("addAudioFilter failed, input video path MediaExtractor failed");
            MediaUtils.closeExtractor(inputVideoExtractor);
            return;
        }
        int srcInputAudioIndex = MediaUtils.getTrackIndex(inputVideoExtractor, TrackType.AUDIO);
        if (srcInputAudioIndex == -5) {
            LogUtils.w("addAudioFilter failed, input video has no audio track");
            MediaUtils.closeExtractor(inputVideoExtractor);
            return;
        }

        //2.抽取输入视频的视频轨道
        int srcInputVideoIndex = MediaUtils.getTrackIndex(inputVideoExtractor, TrackType.VIDEO);
        if (srcInputVideoIndex == -5) {
            LogUtils.w("addAudioFilter failed, input video has no video track");
            MediaUtils.closeExtractor(inputVideoExtractor);
            return;
        }

        //3.抽取输入音频的音频轨道
        MediaExtractor inputAudioExtractor = new MediaExtractor();
        try {
            inputAudioExtractor.setDataSource(params.mInputAudioPath);
        } catch (Exception e) {
            LogUtils.w("addAudioFilter failed, input audio path MediaExtractor failed");
            MediaUtils.closeExtractor(inputVideoExtractor);
            MediaUtils.closeExtractor(inputAudioExtractor);
            return;
        }
        int destInputAudioIndex = MediaUtils.getTrackIndex(inputAudioExtractor, TrackType.AUDIO);
        if (destInputAudioIndex == -5) {
            LogUtils.w("addAudioFilter failed, input audio has no audio track");
            MediaUtils.closeExtractor(inputVideoExtractor);
            MediaUtils.closeExtractor(inputAudioExtractor);
            return;
        }

        //4.开始合成视频
        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(params.mOutputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            MediaUtils.closeExtractor(inputVideoExtractor);
            MediaUtils.closeExtractor(inputAudioExtractor);
            MediaUtils.closeMuxer(mediaMuxer);
            return;
        }

        MediaFormat srcInputVideoFormat = MediaUtils.getFormat(inputVideoExtractor, srcInputVideoIndex);
        int rotation = MediaUtils.getRotation(inputVideoExtractor);
        mediaMuxer.setOrientationHint(rotation);
        int muxerVideoTrackIndex = mediaMuxer.addTrack(srcInputVideoFormat);

        //5.将视频中的音频取出来
        File cacheDir = new File(mContext.getCacheDir(), "audio");
        long timeStamp = System.currentTimeMillis();
        //srcInputAudioFile 将视频中分离的音频保存的位置
        File srcInputAudioFile = new File(cacheDir, "video_" + timeStamp + "_audio" + ".pcm");
        //inputAudioFile 输入的音频转化为pcm
        File inputAudioFile = new File(cacheDir, "audio_" + timeStamp + ".pcm");

        long inputVideoDuration = MediaUtils.getDuration(params.mInputVideoPath);
        if (inputVideoDuration == -1) {
            LogUtils.i("addAudioFilter failed, Input invalid video path");
            return;
        }
        long inputAudioDuration = MediaUtils.getDuration(params.mInputAudioPath);
        if (inputAudioDuration == -1) {
            LogUtils.i("addAudioFilter failed, Input invalid audio path");
            return;
        }
        VideoRange range = params.mVideoRange;
        if (range == null) {
            range = new VideoRange(0, inputVideoDuration);
            params.setVideoRange(range);
        }
        if (range.mStart < 0) {
            range.mStart = 0;
        } else if (range.mEnd < range.mStart) {
            LogUtils.i("The input video range is error");
            return;
        } else if (inputVideoDuration < (range.mEnd - range.mStart)) {
            range.mEnd = inputVideoDuration;
            if (range.mEnd < range.mStart) {
                LogUtils.i("The input video range is error");
                return;
            }
        }
        params.setVideoRange(range);

        final CountDownLatch latch = new CountDownLatch(2);
        WorkThreadHandler.submitRunnableTask(new Runnable() {
            @Override
            public void run() {

                latch.countDown();
            }
        });
    }

}
