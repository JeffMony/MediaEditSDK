package com.video.edit.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.video.edit.demo.R;
import com.video.player.MediaItem;
import com.video.player.MediaUtils;

public class MainActivity2 extends AppCompatActivity implements View.OnClickListener {

    public static final int PERMISSION_REQUEST_CODE = 1000;
    public static final int REQUEST_PICK_CLIP_CODE = 1001;
    public static final int REQUEST_PICK_EDIT_CODE = 1002;
    public static final int REQUEST_PICK_AUDIO_CODE = 1003;
    public static final int REQUEST_PICK_VIDEO_REVERSE_CODE = 1004;
    public static final int REQUEST_PICK_VIDEO_WATERMASK_CODE = 1005;

    private TextView mVideoClipTxt;
    private TextView mVideoFliterTxt;
    private TextView mVideoComposeTxt;
    private TextView mVideoReverseTxt;
    private TextView mVideoWaterMaskTxt;
    private TextView mCameraTxt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVideoClipTxt = findViewById(R.id.tv_start_video_clip);
        mVideoFliterTxt = findViewById(R.id.tv_local_video_filter);
        mVideoComposeTxt = findViewById(R.id.tv_local_video_audio);
        mVideoReverseTxt = findViewById(R.id.tv_local_video_reverse);
        mVideoWaterMaskTxt = findViewById(R.id.tv_local_video_watermark);
        mCameraTxt = findViewById(R.id.tv_camera_preview);

        mVideoClipTxt.setOnClickListener(this);
        mVideoFliterTxt.setOnClickListener(this);
        mVideoComposeTxt.setOnClickListener(this);
        mVideoReverseTxt.setOnClickListener(this);
        mVideoWaterMaskTxt.setOnClickListener(this);
        mCameraTxt.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermission();
    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE && (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "permission has been grunted.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "[WARN] permission is not grunted.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mVideoClipTxt) {
            selectVideo(REQUEST_PICK_CLIP_CODE);
        } else if (v == mVideoFliterTxt) {
            selectVideo(REQUEST_PICK_EDIT_CODE);
        } else if (v == mVideoComposeTxt) {
            selectVideo(REQUEST_PICK_AUDIO_CODE);
        } else if (v == mVideoReverseTxt) {
            selectVideo(REQUEST_PICK_VIDEO_REVERSE_CODE);
        } else if (v == mVideoWaterMaskTxt) {
            selectVideo(REQUEST_PICK_VIDEO_WATERMASK_CODE);
        } else if (v == mCameraTxt) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent(MainActivity2.this, CameraEffectActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity2.this, "目前摄像头预览滤镜效果只支持L以上版本", Toast.LENGTH_LONG).show();
            }

        }
    }

    private void selectVideo(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            MediaItem mediaItem = MediaUtils.getMediaItem(getContentResolver(), data);
            if (mediaItem != null) {
                Intent intent = new Intent();
                intent.putExtra("title", mediaItem.getTitle());
                intent.putExtra("path", mediaItem.getPath());
                intent.putExtra("duration", mediaItem.getDuration());
                intent.putExtra("size", mediaItem.getSize());

                if (requestCode == REQUEST_PICK_CLIP_CODE) {
                    intent.setClass(MainActivity2.this, VideoClipActivity.class);
                } else if (requestCode == REQUEST_PICK_EDIT_CODE) {
                    intent.setClass(MainActivity2.this, VideoFilterActivity.class);
                } else if (requestCode == REQUEST_PICK_AUDIO_CODE) {
                    intent.setClass(MainActivity2.this, VideoAudioFilterActivity.class);
                } else if (requestCode == REQUEST_PICK_VIDEO_REVERSE_CODE) {
                    intent.setClass(MainActivity2.this, VideoReverseEditActivity.class);
                } else if (requestCode == REQUEST_PICK_VIDEO_WATERMASK_CODE) {
                    intent.setClass(MainActivity2.this, VideoWaterMaskActivity.class);
                }
                startActivity(intent);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
