package com.video.process.thread;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.text.TextUtils;

import com.video.process.model.TrackType;
import com.video.process.utils.VideoUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AudioProcessorThread extends Thread {

    private String mInputPath;
    private Integer mStartTime;
    private Integer mEndTime;
    private Float mSpeed;
    private Context mContext;
    private Exception mException;
    private MediaMuxer mMuxer;
    private int mMuxerAudioIndex;
    private MediaExtractor mExtractor;
    private CountDownLatch mMuxerLatch;

    public AudioProcessorThread(Context context, String inputPath,
                                MediaMuxer muxer, int startTime, int endTime,
                                float speed, int muxerAudioIndex,
                                CountDownLatch muxerLatch) {
        super("AudioProcessorThread");
        mContext = context;
        mInputPath = inputPath;
        mMuxer = muxer;
        mStartTime = startTime;
        mEndTime = endTime;
        mSpeed = speed;
        mMuxerAudioIndex = muxerAudioIndex;
        mMuxerLatch = muxerLatch;

    }
    @Override
    public void run() {
        super.run();
    }

    private void doProcessAudio() throws Exception {
        int audioTrackIndex = VideoUtils.getTrackIndex(mExtractor, TrackType.AUDIO);
        if (audioTrackIndex != VideoUtils.ERR_NO_TRACK_INDEX) {
            throw new Exception("Input video has no audio track");
        }
        mExtractor.selectTrack(audioTrackIndex);
        MediaFormat audioFormat = mExtractor.getTrackFormat(audioTrackIndex);
        String inputMimeType = audioFormat.containsKey(MediaFormat.KEY_MIME)
                ? audioFormat.getString(MediaFormat.KEY_MIME) : MediaFormat.MIMETYPE_AUDIO_AAC;
        String outputMimeType = MediaFormat.MIMETYPE_AUDIO_AAC;

        boolean await = mMuxerLatch.await(3, TimeUnit.SECONDS);
        if (!await) {
            throw new TimeoutException("wait muxerStartLatch timeout!");
        }
        int audioDurationUs = audioFormat.getInteger(MediaFormat.KEY_DURATION);
        int startTimeUs = (mStartTime == -1) ? 0 : mStartTime * 1000;
        int endTimeUs = (mEndTime == -1) ? audioDurationUs : mEndTime * 1000;

        if (TextUtils.equals(inputMimeType, outputMimeType)) {

        } else {

        }

    }
}
