package com.video.edit.activity

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.SeekBar
import android.widget.Toast
import com.video.compose.ComposeParams
import com.video.compose.VideoCustomException
import com.video.compose.VideoRange
import com.video.compose.VideoSize
import com.video.epf.custfilter.GlFlashFliter
import com.video.epf.custfilter.GlShakeFilter
import com.video.epf.custfilter.GlSoulOutFilter
import com.video.compose.video.Mp4Composer
import com.video.egl.GlFilterList
import com.video.egl.GlFilterPeriod
import com.video.library.log
import com.video.library.player.VideoPlayTimeController
import com.video.library.player.VideoPlayer
import com.video.library.player.VideoPlayerOfExoPlayer
import com.video.library.player.VideoPlayerOfMediaPlayer
import com.video.library.getVideoDuration
import com.video.library.showToast
import kotlinx.android.synthetic.main.activity_video_clip.*
import java.io.File
import java.text.DecimalFormat

import com.video.edit.view.ClipContainer
import com.video.edit.demo.R
import com.video.edit.ext.SdkConfig

/**
 * 请根据手机中视频文件的地址更新下面的videoPlayUrl变量
 */
class VideoClipActivity : AppCompatActivity(), ClipContainer.Callback {

    companion object {
        val TAG = "VideoClipActivity"
    }
    var outputPath: String = ""
    var saveDir: String = ""

    lateinit var videoPathInput: String
    lateinit var finalVideoPath: String
    var videoPlayer: VideoPlayer? = null

    var handler = object : Handler() {
        override fun handleMessage(msg: Message?) {
            updatePlayPosition()
        }
    }
    private var millsecPerThumbnail = 1000
    private var secFormat = DecimalFormat("##0.0")

    var mediaDuration: Long = 0
    var startMillSec: Long = 0
    var endMillSec: Long = 0
    var frozontime = 0L
    var useSmoothPreview = false
    var thumbnailCount = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_clip)
        saveDir = SdkConfig.getVideoDir(this).absolutePath
        videoPathInput = intent.getStringExtra("video_path")
        Log.d(TAG, "onCreate videoPathInput:$videoPathInput")

        initPlayer()

        play_spped_seakbar.max = SdkConfig.SPEED_RANGE
        var normalSpeed = play_spped_seakbar.max / 2
        play_spped_seakbar.progress = normalSpeed

        play_spped_seakbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var speed = 1.0f + progress / 10f * 1f
                if (progress > normalSpeed) {
                    speed = 1.0f + (progress - normalSpeed) * 1f / normalSpeed
                } else {
                    speed = progress * 1f / normalSpeed + 0.01f
                }

                Log.d(TAG, "onProgressChanged  progress:$progress, speed:$speed")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setPlayerSpeed(speed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 因为使用了egl, 必须在一个新线程中启动
        if (useSmoothPreview) {
            startProcess()
        } else {
            finalVideoPath = videoPathInput
            hideShadow()
            onProcessCompleted()
        }


        tv_framepreviewmode.setOnClickListener {
            swithToFramePreviewMode()
        }

        tv_clip.setOnClickListener {
            Log.d(TAG, "startMillSec:$startMillSec,  endMillSec:$endMillSec,  mediaDuration:$mediaDuration")
            if (mediaDuration > 0 && endMillSec <= mediaDuration
                    && startMillSec >= 0
                    && endMillSec <= startMillSec + SdkConfig.maxSelection
                    && endMillSec >= startMillSec + SdkConfig.minSelection) {
                doClip()
            } else {
                showToast("裁剪选择时间段不正确哟")
            }
        }
    }


    private fun swithToFramePreviewMode() {
        showShadow()
        startProcess()
    }

    private fun setPlayerSpeed(speed: Float) {
        videoPlayer!!.setPlaySpeed(speed)
    }


    private fun startProcess() {
        outputPath = saveDir + File.separator + System.currentTimeMillis() + ".mp4"
        var composeParams = ComposeParams(videoPathInput, outputPath)
        composeParams.setFrameRate(5)
        var mp4Composer = Mp4Composer(composeParams)
        mp4Composer.listener(object : Mp4Composer.VideoComposeListener {
            override fun onProgress(progress: Double) {
                runOnUiThread { pb_progress.progress = (progress * 100).toInt() }
            }

            override fun onCompleted() {
                Log.d(TAG, "onCompleted()")
                runOnUiThread {
                    hideShadow()

                    finalVideoPath = outputPath
                    onProcessCompleted()

                    videoPlayer!!.enableFramePreviewMode()
                    tv_framepreviewmode.visibility = View.INVISIBLE
                }
            }

            override fun onFailed(exception: VideoCustomException) {
                Log.d(TAG, "onFailed()")
            }

            override fun onCanceled() {
                TODO("Not yet implemented")
            }
        });
        mp4Composer.start()
    }

    private fun hideShadow() {
        pb_progress.visibility = View.GONE
        view_shadow.visibility = View.INVISIBLE
    }

    private fun showShadow() {
        pb_progress.visibility = View.VISIBLE
        view_shadow.visibility = View.VISIBLE
    }


    private fun onProcessCompleted() {

        var file = File(finalVideoPath)
        if (!file.exists()) {
            Toast.makeText(this, "请更新videoPlayUrl变量为本地手机的视频文件地址", Toast.LENGTH_LONG).show()
        }


        mediaDuration = getVideoDuration(this, finalVideoPath)
        Log.d(TAG, "onProcessCompleted mediaDuration:$mediaDuration")
        endMillSec = if (mediaDuration > SdkConfig.maxSelection) {
            SdkConfig.maxSelection
        } else {
            mediaDuration
        }

        thumbnailCount = if (mediaDuration > SdkConfig.maxSelection) {
            millsecPerThumbnail = SdkConfig.MAX_FRAME_INTERVAL_MS
            Math.ceil(((mediaDuration * 1f / millsecPerThumbnail).toDouble())).toInt()
        } else {
            millsecPerThumbnail = (mediaDuration / SdkConfig.DEFAULT_FRAME_COUNT).toInt()
            SdkConfig.DEFAULT_FRAME_COUNT
        }

        clipContainer.initRecyclerList(thumbnailCount)


        if (videoPlayer?.isPlaying() == true) {
            releasePlayer()
        }

        setupPlayer()

        clipContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                log("onGlobalLayout()  mediaDuration:$mediaDuration,  size:${clipContainer.mList.size}")
                clipContainer.updateInfo(mediaDuration, clipContainer.mList.size)
                updatePlayPosition()

                clipContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        clipContainer.mCallback = (this)
    }

    private fun releasePlayer() {
        videoPlayer?.releasePlayer()
    }


    override fun onPreviewChange(startMillSec: Long, finished: Boolean) {
        var selSec = startMillSec / 1000f
        toast_msg_tv.text = "预览到${secFormat.format(selSec)}s"
        toast_msg_tv.visibility = View.VISIBLE
        if (!finished) {
            pausePlayer()
        }

        seekToPosition(startMillSec)


        if (finished) {
            frozontime = System.currentTimeMillis() + 500
            startPlayer()
        }

        handler.removeMessages(SdkConfig.MSG_UPDATE)
        if (finished) {
            handler.sendEmptyMessageDelayed(SdkConfig.MSG_UPDATE, 20)
        }
    }


    override fun onSelectionChange(totalCount: Int, _startMillSec: Long, _endMillSec: Long, finished: Boolean) {
        Log.d(TAG, "onSelectionChang ...startMillSec:$_startMillSec, endMillSec:$_endMillSec")
        this.startMillSec = _startMillSec
        this.endMillSec = _endMillSec

        var time = (endMillSec - startMillSec)
        if (time > mediaDuration) {
            time = mediaDuration
        }
        adjustSelection()

        var selSec = time / 1000f
        toast_msg_tv.text = "已截取${secFormat.format(selSec)}s, [$startMillSec - $endMillSec]"
        toast_msg_tv.visibility = View.VISIBLE

        handler.removeMessages(SdkConfig.MSG_UPDATE)
        if (finished) {
            handler.sendEmptyMessageDelayed(SdkConfig.MSG_UPDATE, 20)
        }

        if (!finished) {
            pausePlayer()
        }

        seekToPosition(startMillSec)

        if (finished) {
            frozontime = System.currentTimeMillis() + 500
            startPlayer()
            videoPlayTimeController?.setPlayTimeRange(startMillSec, endMillSec)
        }
    }

    private fun adjustSelection() {
        if (endMillSec > mediaDuration) {
            endMillSec = mediaDuration
        }
        if (startMillSec < 0) {
            startMillSec = 0
        }

        if (startMillSec + SdkConfig.minSelection > endMillSec && endMillSec < mediaDuration) {
            endMillSec = Math.min(startMillSec + SdkConfig.minSelection, mediaDuration)
            if (startMillSec + SdkConfig.minSelection > endMillSec && startMillSec > 0) {
                startMillSec = Math.max(0, endMillSec - SdkConfig.minSelection)
            }
        }
    }


    override fun onPause() {
        super.onPause()
        pausePlayer()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        player_view_exo_thumbnail.release()
        videoPlayTimeController?.stop()
    }

    fun updatePlayPosition() {

        val currentPosition = getPlayerCurrentPosition()
        if (currentPosition > endMillSec) {
            seekToPosition(0)
        } else {
            clipContainer.setProgress(currentPosition.toLong(), frozontime)
        }

        handler.removeMessages(SdkConfig.MSG_UPDATE)
        handler.sendEmptyMessageDelayed(SdkConfig.MSG_UPDATE, 20)
    }

    private fun startPlayer() {
        videoPlayer?.startPlayer()
    }

    private fun seekToPosition(startMillSec: Long) {
        videoPlayer?.seekToPosition(startMillSec)
    }

    private fun pausePlayer() {
        videoPlayer?.pausePlayer()
    }

    private fun getPlayerCurrentPosition(): Int {
        return videoPlayer!!.getPlayerCurrentPosition()
    }

    var videoPlayTimeController: VideoPlayTimeController? = null
    private fun setupPlayer() {
        videoPlayer?.setupPlayer(this, finalVideoPath)

        videoPlayTimeController = VideoPlayTimeController(videoPlayer!!)
        videoPlayTimeController?.start()

        player_view_exo_thumbnail.setDataSource(finalVideoPath, millsecPerThumbnail, thumbnailCount) { bitmap: String, index: Int ->
            handler.post { clipContainer.addThumbnail(index, bitmap) }
        }
    }

    private fun initPlayer() {
        if (SdkConfig.USE_EXOPLAYER) {
            player_view_mp.visibility = View.GONE
            player_view_exo.visibility = View.VISIBLE
            videoPlayer = VideoPlayerOfExoPlayer(player_view_exo)
        } else {
            player_view_mp.visibility = View.VISIBLE
            player_view_exo.visibility = View.GONE
            videoPlayer = VideoPlayerOfMediaPlayer(player_view_mp)
        }

        videoPlayer?.initPlayer()


    }


    private fun doClip() {
        showShadow()

        doClipUseGl()

    }

    private fun doClipUseGl() {
        var glFilterList = GlFilterList()
        glFilterList.putGlFilter(GlFilterPeriod(0, 2000, GlSoulOutFilter(this)))
        glFilterList.putGlFilter(GlFilterPeriod(2000, 4000, GlFlashFliter(this)))
        glFilterList.putGlFilter(GlFilterPeriod(4000, 6000, GlShakeFilter(this)))
        outputPath = saveDir + File.separator + System.currentTimeMillis() + ".mp4";
        var composeParams = ComposeParams(videoPathInput, outputPath)
        composeParams.setFrameRate(8)
        composeParams.setFilterList(glFilterList)
        composeParams.setDestVideoSize(VideoSize(540, 960))
        composeParams.setVideoRange(VideoRange(startMillSec, endMillSec))
        var mp4Composer = Mp4Composer(composeParams)
        mp4Composer.listener(object : Mp4Composer.VideoComposeListener {
            override fun onProgress(progress: Double) {
                Log.d(TAG, "onProgress = $progress")
                runOnUiThread { pb_progress.progress = (progress * 100).toInt() }
            }

            override fun onCompleted() {
                Log.d(TAG, "onCompleted()")
                runOnUiThread {
                    hideShadow()
                    showToast("裁剪成功!新文件已经存放在:" + outputPath)
                    finish()
                }
            }

            override fun onFailed(exception: VideoCustomException) {
                Log.d(TAG, "clip onFailed", exception)
                runOnUiThread {
                    hideShadow()
                    showToast("裁剪失败")
                }
            }

            override fun onCanceled() {
                TODO("Not yet implemented")
            }
        })
        mp4Composer.start()
    }
}
