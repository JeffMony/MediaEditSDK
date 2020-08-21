package com.video.edit.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ui.PlayerView;
import com.video.edit.ext.MusicConfigs;
import com.video.edit.ext.PreferenceUtils;
import com.video.edit.ext.SdkConfig;
import com.video.edit.view.BottomDialogFragment;
import com.video.process.utils.LogUtils;
import com.video.edit.demo.R;
import com.video.player.player.VideoPlayer;
import com.video.player.player.VideoPlayerOfExoPlayer;

import java.io.File;

public class VideoAudioFilterActivity extends AppCompatActivity {

    private PlayerView mPlayerView;
    private TextView mMusicTv;
    private String mMediaPath;
    private VideoPlayer mVideoPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_filter);
        mMediaPath = getIntent().getStringExtra("video_path");
        LogUtils.i("MediaPath = " + mMediaPath);

        mPlayerView = findViewById(R.id.player_view);
        mMusicTv = findViewById(R.id.tv_video_music);

        mMusicTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMusicDialog();
            }
        });

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
    protected void onDestroy() {
        super.onDestroy();
        mVideoPlayer.releasePlayer();
    }

    private void showMusicDialog() {
        BottomDialogFragment dialogFragment = BottomDialogFragment.getInstance(0, getSelection(),
                "选择音乐", MusicConfigs.createMusicOptions());
        dialogFragment.setSelectionCallback(new BottomDialogFragment.SelectionCallback() {
            @Override
            public void onSelected(int select, BottomDialogFragment.Option option) {
                LogUtils.i("" + option.mOptionName + ", " + option.mIndex);
            }
        });
        dialogFragment.show(getSupportFragmentManager(), SdkConfig.MUSIC_DIALOG);
    }

    private int getSelection() {
        return PreferenceUtils.getInt(this, PreferenceUtils.MUSIC_SELECTION_KEY, 0);
    };
}
