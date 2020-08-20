package com.video.edit.activity;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ui.PlayerView;
import com.video.process.utils.LogUtils;
import com.video.edit.demo.R;
import com.video.player.player.VideoPlayer;
import com.video.player.player.VideoPlayerOfExoPlayer;

import java.io.File;

public class VideoReverseEditActivity extends AppCompatActivity {

    private PlayerView mPlayerView;
    private String mMediaPath;
    private VideoPlayer mVideoPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_reverse);
        mMediaPath = getIntent().getStringExtra("video_path");
        LogUtils.i("MediaPath = " + mMediaPath);

        mPlayerView = findViewById(R.id.player_view);

        File mediaFile = new File(mMediaPath);
        if (!mediaFile.exists()) {
            Toast.makeText(this, "请更新videoPlayUrl变量为本地手机的视频文件地址", Toast.LENGTH_LONG).show();
        }
        mVideoPlayer = new VideoPlayerOfExoPlayer(mPlayerView);
        mVideoPlayer.setupPlayer(this, mMediaPath);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoPlayer.pausePlayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mVideoPlayer.releasePlayer();
    }
}
