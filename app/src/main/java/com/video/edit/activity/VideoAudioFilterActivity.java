package com.video.edit.activity;

import android.content.res.AssetFileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ui.PlayerView;
import com.video.edit.ext.MusicConfigs;
import com.video.edit.ext.PreferenceUtils;
import com.video.edit.ext.SdkConfig;
import com.video.edit.view.BottomDialogFragment;
import com.video.process.VideoProcessorManager;
import com.video.process.model.ProcessParams;
import com.video.process.utils.LogUtils;
import com.video.edit.demo.R;
import com.video.player.player.VideoPlayer;
import com.video.player.player.VideoPlayerOfExoPlayer;
import com.video.process.utils.VideoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

public class VideoAudioFilterActivity extends AppCompatActivity {

    private PlayerView mPlayerView;
    private TextView mMusicTv;
    private TextView mNextTv;
    private String mMediaPath;
    private VideoPlayer mVideoPlayer;
    private String mOptionName;
    private String mSaveDir;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_filter);
        mMediaPath = getIntent().getStringExtra("video_path");
        LogUtils.i("MediaPath = " + mMediaPath);

        mSaveDir = SdkConfig.getVideoDir(this).getAbsolutePath();

        mPlayerView = findViewById(R.id.player_view);
        mMusicTv = findViewById(R.id.tv_video_music);
        mNextTv = findViewById(R.id.tv_next);
        VideoProcessorManager.getInstance().initContext(this);
        mMusicTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMusicDialog();
            }
        });

        mNextTv.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View v) {
                LogUtils.i("----------------");
                String fileName = MusicConfigs.getMusicByType(mOptionName);
                LogUtils.i("fileName = " + fileName);
                String localFilePath = mSaveDir + File.separator + fileName;
                LogUtils.i("localFilePath = " + localFilePath);
//                try {
//                    copyAssets(fileName, localFilePath);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    return;
//                }
                String outputFilePath = mSaveDir + File.separator + System.currentTimeMillis() + ".mp4";
                ProcessParams params = new ProcessParams(mMediaPath, outputFilePath);
                params.setInputAudioPath(localFilePath);
                VideoProcessorManager.getInstance().replaceAudioTrack(params);
            }
        });

        File mediaFile = new File(mMediaPath);
        if (!mediaFile.exists()) {
            Toast.makeText(this, "请更新videoPlayUrl变量为本地手机的视频文件地址", Toast.LENGTH_LONG).show();
        }
        mVideoPlayer = new VideoPlayerOfExoPlayer(mPlayerView);
        mVideoPlayer.setupPlayer(this, mMediaPath);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void copyAssets(String fileName, String filePath) throws Exception {
        AssetFileDescriptor assetFileDescriptor = null;
        File outputFile = new File(filePath);
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel from = null;
        FileChannel to = null;
        try {
            assetFileDescriptor = getAssets().openFd(fileName);
            fis = new FileInputStream(assetFileDescriptor.getFileDescriptor());
            from = fis.getChannel();
            fos = new FileOutputStream(outputFile);
            to = fos.getChannel();
            LogUtils.i("copyAssets size = " + from.size());
            from.transferTo(0, from.size(), to);
        } catch (Exception e) {
            throw e;
        } finally {
            VideoUtils.close(fis);
            VideoUtils.close(fos);
            VideoUtils.close(assetFileDescriptor);
            VideoUtils.close(from);
            VideoUtils.close(to);
        }
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
                mOptionName = option.mOptionName;
            }
        });
        dialogFragment.show(getSupportFragmentManager(), SdkConfig.MUSIC_DIALOG);
    }

    private int getSelection() {
        return PreferenceUtils.getInt(this, PreferenceUtils.MUSIC_SELECTION_KEY, 0);
    };
}
