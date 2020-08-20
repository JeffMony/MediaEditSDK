package com.video.edit.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.video.library.log
import com.video.library.VideoItem
import com.video.library.getVideoItem
import kotlinx.android.synthetic.main.activity_main.*

import com.video.edit.demo.R

class MainActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1000
        const val REQUEST_PICK_CLIP_CODE = 1001
        const val REQUEST_PICK_EDIT_CODE = 1002
        const val REQUEST_PICK_AUDIO_CODE = 1003
        const val REQUEST_PICK_VIDEO_REVERSE = 1004
        const val REQUEST_PICK_VIDEO_WATERMASK = 1005
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 裁剪视频
        tv_start_video_clip.setOnClickListener { selectVideo(REQUEST_PICK_CLIP_CODE) }

        // 编辑视频(特效 滤镜)
        tv_local_video_filter.setOnClickListener { selectVideo(REQUEST_PICK_EDIT_CODE) }

        tv_local_video_audio.setOnClickListener { selectVideo(REQUEST_PICK_AUDIO_CODE) }

        tv_local_video_reverse.setOnClickListener { selectVideo(REQUEST_PICK_VIDEO_REVERSE) }

        tv_local_video_watermark.setOnClickListener { selectVideo(REQUEST_PICK_VIDEO_WATERMASK) }

        tv_camera_preview.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(this, CameraEffectActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this@MainActivity, "目前摄像头预览滤镜效果只支持L以上版本", Toast.LENGTH_LONG).show()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    /**
     * 从系统中选择视频
     */
    private fun selectVideo(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, requestCode)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            val videoItem = getVideoItem(contentResolver, data!!)
            videoItem?.run {
                log("video title:$title, duration:${durationSec}, size:$size, path:$path")
                when (requestCode) {
                    REQUEST_PICK_CLIP_CODE -> startActivity(this, VideoClipActivity::class.java)
                    REQUEST_PICK_EDIT_CODE -> startActivity(this, VideoFilterActivity::class.java)
                    REQUEST_PICK_AUDIO_CODE -> startActivity(this, VideoAudioFilterActivity::class.java)
                    REQUEST_PICK_VIDEO_REVERSE -> startActivity(this, VideoReverseEditActivity::class.java)
                    REQUEST_PICK_VIDEO_WATERMASK -> startActivity(this, VideoWaterMaskActivity::class.java)
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startActivity(videoItem: VideoItem, activityClass: Class<*>) {
        startActivity(Intent(this, activityClass).apply {
            videoItem?.run {
                putExtra("video_path", path)
                putExtra("video_duration", durationSec)
            }
        })
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        // request permission if it has not been grunted.
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            return false
        }

        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this@MainActivity, "permission has been grunted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "[WARN] permission is not grunted.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
