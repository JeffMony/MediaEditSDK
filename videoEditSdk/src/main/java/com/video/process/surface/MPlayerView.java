package com.video.process.surface;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.video.process.model.VideoSize;
import com.video.process.preview.filter.GlFilterList;
import com.video.process.preview.filter.GlFilterPeriod;
import com.video.process.utils.LogUtils;
import com.video.process.utils.ScreenUtils;
import com.video.process.utils.WorkThreadHandler;
import com.video.process.preview.filter.GlFilter;
import com.video.player.player.mp.TextureSurfaceRenderer2;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static android.media.MediaPlayer.SEEK_CLOSEST;

public abstract class MPlayerView extends FrameLayout implements
        TextureView.SurfaceTextureListener,
        MediaPlayer.OnPreparedListener {
    private static final String TAG = "MPlayerView";
    private Context mContext;
    private FrameLayout mContainer;
    private TextureView mTextureView;
    protected MediaPlayer mMediaPlayer;
    private String mUrl;

    private int surfaceWidth, surfaceHeight;
    private EncoderSurface encoderSurface;
    private DecoderOutputSurface decoderSurface;
    private GlFilterList filterList = null;

    protected long currentPostion = 0L;
    protected VideoSize mVideoSize;

    public MPlayerView(Context context) {
        this(context, null);
    }

    public MPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    private void init() {
        mContainer = new FrameLayout(mContext);
        mContainer.setBackgroundColor(Color.BLACK);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mContainer, params);

        filterList = new GlFilterList();
    }

    public GlFilterList getFilterList() {
        return filterList;
    }

    public GlFilterPeriod setFiler(long startTimeMs, long endTimeMs, GlFilter glFilter) {

        GlFilterPeriod period = new GlFilterPeriod(startTimeMs, endTimeMs, glFilter);
        filterList.putGlFilter(period);
        return period;
    }

    public GlFilterPeriod addFiler(long startTimeMs, long endTimeMs, GlFilter glFilter) {
        GlFilterPeriod period = new GlFilterPeriod(startTimeMs, endTimeMs, glFilter);
        filterList.putGlFilter(period);
        return period;
    }

    public void setDataSource(final String url) throws ExecutionException, InterruptedException {
        mUrl = url;
        mVideoSize = (VideoSize) WorkThreadHandler.submitCallbackTask(new Callable() {
            @Override
            public Object call() throws Exception {
                MediaMetadataRetriever retriever =  new MediaMetadataRetriever();
                retriever.setDataSource(url);
                int width = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height = Integer.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                VideoSize videoSize = new VideoSize(width, height);
                return videoSize;
            }
        }).get();
        int screenWidth = ScreenUtils.getScreenWidth(getContext());
        surfaceWidth = screenWidth;
        surfaceHeight = (int)(mVideoSize.mHeight * surfaceWidth * 1.0f / mVideoSize.mWidth);
        LogUtils.w(TAG + ", videoWidth = " + mVideoSize.mWidth + ", videoHeight = " + mVideoSize.mHeight);
        LogUtils.w(TAG + ", surfaceWidth = " + surfaceWidth + ", surfaceHeight = " + surfaceHeight);
    }

    public void start() {
        initTextureView();
        addTextureView();
        running = true;
    }


    private void initTextureView() {
        if (mTextureView == null) {
            mTextureView = new TextureView(mContext);
            mTextureView.setSurfaceTextureListener(this);
        }
    }

    private void addTextureView() {
        mContainer.removeView(mTextureView);
        LayoutParams params = new LayoutParams(surfaceWidth, surfaceHeight);
        params.gravity = Gravity.CENTER;
        mContainer.addView(mTextureView, 0, params);
    }

    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            Log.d(TAG, "initMediaPlayer: ...");
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setLooping(true);

            while (decoderSurface.getSurface() == null) {
                try {
                    Thread.sleep(30);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            Surface surface = decoderSurface.getSurface();
            try {
                mMediaPlayer.setDataSource(mUrl);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mMediaPlayer.setSurface(surface);

            mMediaPlayer.prepareAsync();
        }
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: ...");

        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                encoderSurface = new EncoderSurface(new Surface(surface));
                encoderSurface.makeCurrent();
                decoderSurface = new DecoderOutputSurface(new GlFilter(), filterList);
                decoderSurface.setOutputVideoSize(new VideoSize(width, height));
                decoderSurface.setInputResolution(new VideoSize(mVideoSize.mWidth, mVideoSize.mHeight));
                decoderSurface.setupAll();
                post(new Runnable() {
                    @Override
                    public void run() {
                        initMediaPlayer();
                    }
                });
                poll();
            }
        });
        th.start();


    }

    private volatile boolean running = false;
    protected volatile boolean notDestroyed = true;

    private void poll() {
        while (notDestroyed) {
            if (running) {
                try {
                    decoderSurface.awaitNewImage();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return;
                }
                decoderSurface.drawImage(currentPostion * 1000 * 1000);
                encoderSurface.setPresentationTime(System.currentTimeMillis());
                encoderSurface.swapBuffers();
            } else {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        notDestroyed = false;
        running = false;
        decoderSurface.stopRun();
    }

    public abstract TextureSurfaceRenderer2 getVideoRender(SurfaceTexture surface, int surfaceWidth, int surfaceHeight);


    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "onPrepared: ...");
        mp.start();
    }

    public void resumePlay() {
        running = true;
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    public void pausePlay() {
        running = false;
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.pause();
            } catch (Exception ex) {
            }
        }
    }

    public void release() {
        notDestroyed = false;
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
        decoderSurface.stopRun();
    }

    public void seekTo(long timems) {
        if (mMediaPlayer != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mMediaPlayer.seekTo(timems, SEEK_CLOSEST);
            } else {
                mMediaPlayer.seekTo((int) timems);
            }
        }
    }

}
