package com.video.edit.activity

import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.video.process.model.ProcessParams
import com.video.process.utils.VideoCustomException
import com.video.process.preview.filter.FilterType
import com.video.process.preview.filter.GlFilterList
import com.video.process.preview.filter.GlFilterPeriod
import com.video.process.compose.video.Mp4Composer
import com.video.process.surface.VideoProcessConfig
import kotlinx.android.synthetic.main.video_process_activity_layout.*

import com.video.edit.demo.R

class VideoProgressActivity : AppCompatActivity() {
    companion object {
        const val TAG = "VideoProgressActivity"
    }

    lateinit var videoProcessConfig: VideoProcessConfig
    var glFilterList = GlFilterList()

    var handler = Handler()
    var progression = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.video_process_activity_layout)

        videoProcessConfig = intent.getSerializableExtra("videoProcessConfig") as VideoProcessConfig
        Log.d(TAG, "on create mediaPath:${videoProcessConfig.srcMediaPath}")

        val filterConfigList = videoProcessConfig.filterConfigList

        for (fconfig in filterConfigList){
            glFilterList.putGlFilter(GlFilterPeriod(fconfig.startTimeMs, fconfig.endTimeMs, FilterType.createGlFilter(fconfig.filterName, null, this)))
        }
    }

    override fun onResume() {
        super.onResume()
        var s = System.currentTimeMillis();
        var composeParams = ProcessParams(videoProcessConfig.srcMediaPath, videoProcessConfig.outMediaPath)
        composeParams.setFrameRate(30)
        composeParams.setFilterList(glFilterList)
        var mp4Composer = Mp4Composer(composeParams)
        mp4Composer.listener(object : Mp4Composer.VideoComposeListener {
            override fun onProgress(_p: Double) {
                Log.d(TAG, "onProgress $_p")
                progression = (100 * _p).toInt()
            }

            override fun onCompleted() {
                Log.d(TAG, "onCompleted()")
                runOnUiThread {
                    var e = System.currentTimeMillis()
                    Toast.makeText(this@VideoProgressActivity, "生成视频成功,耗时${e-s}ms, 文件放在:${videoProcessConfig.outMediaPath}", Toast.LENGTH_LONG).show()
                }
                progression = 100
                finish()
            }

            override fun onCanceled() {
                runOnUiThread {
                    Toast.makeText(this@VideoProgressActivity, "生成视频取消", Toast.LENGTH_LONG).show()
                }

            }

            override fun onFailed(exception: VideoCustomException) {
                Log.d(TAG, "onFailed()")
                runOnUiThread {
                    Toast.makeText(this@VideoProgressActivity, "生成视频失败", Toast.LENGTH_LONG).show()
                }

            }
        })
        mp4Composer.start()
        showProgress()
    }

    private fun showProgress() {
        if (progression > 100) {
            return
        }
        pb_progress.progress = progression
        tv_progress.text = "$progression%"
        if (progression <= 100) {
            handler.postDelayed({ showProgress() }, 200)
        }

    }
}