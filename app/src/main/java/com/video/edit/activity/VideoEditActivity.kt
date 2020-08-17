package com.video.edit.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_video_edit.*

import com.video.egl.GlFilterPeriod
import com.video.egl.GlFilterConfig
import com.video.egl.VideoProcessConfig

import com.video.edit.demo.R
import com.video.edit.ext.*
import com.video.edit.view.BaseThumbnailAdapter
import com.video.edit.view.BottomDialogFragment
import com.video.edit.view.getScollXDistance
import com.video.library.getVideoDuration
import com.video.library.toTime

class VideoEditActivity : AppCompatActivity() {


    companion object {
        const val TAG = "VideoEditActivity"
        const val STATE_NORMAL = 0
        const val STATE_FILTER = 1
        const val STATE_EFFECT = 2
    }

    lateinit var mediaPath: String
    var mediaDuration: Long = 0
    var thumbnailCount = 0
    private var millsecPerThumbnail = 1000
    var list: MutableList<String?> = mutableListOf()
    var itemWidth = 100
    var mIsTouching = false
    var adapter: BaseThumbnailAdapter? = null

    var effectTouching = false
    var effectStartTime = 0L
    var effectEndTime = 0L
    //    var effectFliter: GlFilter? = null
    var effectFilterPeriod: GlFilterPeriod? = null

    var state = STATE_NORMAL

    lateinit var videoProcessConfig :com.video.egl.VideoProcessConfig
    lateinit var filterConfigList:MutableList<GlFilterConfig>

//    var glFilterList = GlFilterList()

    var effectTouchListener = View.OnTouchListener { v, event ->
        var option = v.tag as BottomDialogFragment.Option
        val eventId = event.getAction()
        when (eventId) {
            MotionEvent.ACTION_DOWN -> {
                effectTouching = true
                effectStartTime = currentPlayTime
//                Log.d(TAG, "effectStartTime: $effectStartTime")
                beginOneEffect(option)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                effectTouching = false
                effectEndTime = currentPlayTime
//                Log.d(TAG, "effectEndTime: $effectEndTime")
                endOneEffect()
            }
        }
        true
    }

    var handler = object : Handler() {
        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_video_edit)

        mediaPath = intent.getStringExtra("video_path")

        player_view_mp.setDataSource(mediaPath)
        player_view_mp.start()

        tv_filter.setOnClickListener { showFilterDialog() }
        tv_effect.setOnClickListener { switchToEffectEdit() }
        tv_next.setOnClickListener { generateVideo() }

        initEditInfo()

        videoProcessConfig = VideoProcessConfig(mediaPath, VideoClipActivity.videoPlayUrl)
        filterConfigList = videoProcessConfig.filterConfigList
    }

    private fun generateVideo() {

        player_view_mp.pausePlay()
        player_view_mp.release()

        startActivity(Intent(this, VideoProgressActivity::class.java).apply {
            putExtra("videoProcessConfig", videoProcessConfig)
        })
        finish()
    }

    private fun doGenerate() {

    }

    private fun initEditInfo() {
        mediaDuration = getVideoDuration(this, mediaPath)
        Log.d(TAG, "initEditInfo mediaDuration:$mediaDuration")
        millsecPerThumbnail = 500
        thumbnailCount = Math.ceil(((mediaDuration * 1f / millsecPerThumbnail).toDouble())).toInt()
        Log.d(TAG, "thumbnailCount:$thumbnailCount,  millsecPerThumbnail:$millsecPerThumbnail")
        for (i in 0 until thumbnailCount) {
            list.add(i, "")
        }
        var screenW = resources.displayMetrics.widthPixels
        itemWidth = screenW / 12

        adapter = BaseThumbnailAdapter(list, itemWidth)
        recyclerview.adapter = adapter
        var layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this).apply {
            orientation = androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
        }
        recyclerview.layoutManager = layoutManager

        var padding = screenW / 2
        recyclerview.setPaddingRelative(padding, 0, padding, 0)
        recyclerview.clipChildren = false

        recyclerview.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
//                Log.d(TAG, "onScrolled  dx:" + dx)
                val (position, itemLeft, scrollX) = recyclerView.getScollXDistance()
                var total = itemWidth * adapter!!.itemCount
                var rate = 1f * (scrollX + padding) / total
                if (position == -1) {
                    rate = 1f
                }
//                Log.d(TAG, "onScrolled: position:$position, itemLeft:$itemLeft,  scrollX:$scrollX, total:$total, rate:$rate")
                onPreview(rate)
            }
        })

        recyclerview.setOnTouchListener { v, event ->
            val eventId = event.getAction()
            when (eventId) {
                MotionEvent.ACTION_DOWN -> mIsTouching = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mIsTouching = false
            }
            false
        }

        player_view_exo_thumbnail.setDataSource(mediaPath, millsecPerThumbnail, thumbnailCount) { bitmap: String, index: Int ->
            Log.d(TAG, "[$index]bitmap:$bitmap")
            handler.post {
                list.set(index, bitmap)
                adapter!!.notifyDataSetChanged()
            }
        }

        player_view_mp.setProgressListener() { timeMs ->
            onPlayPositionChanged(timeMs)
        }
    }

    var currentPlayTime = 0L
    var lastTimeMs = 0L
    private fun onPlayPositionChanged(timeMs: Long) {

        tv_play_position.text = timeMs.toTime()

        if (mIsTouching) {
            lastTimeMs = timeMs
            return
        }
        var diff = timeMs - lastTimeMs
        var rate = diff * 1f / mediaDuration
        var total = itemWidth * (adapter?.itemCount ?: 0)
        var widthDiff = total * 1f * rate
//        Log.d(TAG, "playing timeMs:$timeMs, rate:$rate, widthDiff:$widthDiff")
        recyclerview.scrollBy(widthDiff.toInt(), 0)

        lastTimeMs = timeMs
        currentPlayTime = timeMs
    }

    fun onPreview(rate: Float) {
        var timems = mediaDuration * rate
//        Log.d(TAG, "onPreview  time:$timems")
        if (mIsTouching) {
            player_view_mp.seekTo(timems.toLong())
            lastTimeMs = timems.toLong()
        }

    }


    private fun beginOneEffect(option: BottomDialogFragment.Option) {
        val filter = EffectConfigs.getEffectFilterByName(option.mOptionName, applicationContext)
        Log.d(TAG, "beginOneEffect option:${option.mOptionName}  effectStartTime:$effectStartTime, filter:$filter")

        effectFilterPeriod = player_view_mp.addFiler(effectStartTime, mediaDuration, filter)
//        effectFliter = filter

    }

    private fun endOneEffect() {
        Log.d(TAG, "endOneEffect effectEndTime:$effectEndTime")
        effectFilterPeriod!!.endTimeMs = effectEndTime
//        glFilterList.putGlFilter(GlFilterPeriod(effectFilterPeriod!!.startTimeMs, effectEndTime, effectFilterPeriod!!.filter))
        filterConfigList.add(GlFilterConfig(effectFilterPeriod!!.filter.type, effectFilterPeriod!!.startTimeMs, effectEndTime))
    }


    private fun switchToEffectEdit() {
//        showToast("特效还为开发完成")
        if (state == STATE_EFFECT) {
            state = STATE_NORMAL
            recyclerview.visibility = View.INVISIBLE
            iv_effect_framebar.visibility = View.INVISIBLE
            hs_effect_list.visibility = View.INVISIBLE
            return
        }
//        player_view_mp.scale()
        recyclerview.visibility = View.VISIBLE
        iv_effect_framebar.visibility = View.VISIBLE

        var options = EffectConfigs.createEffectOptions()

        hs_effect_list.visibility = View.VISIBLE

        options.forEachIndexed { index, option ->
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_record_beauty, null)
            itemView.findViewById<ImageView>(R.id.iv_beauty_image).setImageResource(option.mIconResId)
            itemView.findViewById<TextView>(R.id.tv_beauty_text).text = option.mOptionName
            itemView.tag = option
            option.mIndex = index
            ll_container.addView(itemView)

            itemView.setOnTouchListener(effectTouchListener)
        }

        state = STATE_EFFECT
    }

    override fun onResume() {
        super.onResume()
        player_view_mp.resumePlay()
    }

    override fun onPause() {
        super.onPause()
        player_view_mp.pausePlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        player_view_mp.release()
    }


    private fun showFilterDialog() {
        var dialogFragment = BottomDialogFragment.getInstance(0, getSelection(),
                "选择滤镜", FilterConfigs.createFilterOptions())
        dialogFragment.setSelectionCallback() { selection, option ->
            val filter = FilterConfigs.getFilterByName(option.mOptionName, applicationContext)
            Log.d(TAG, "selection:$selection, filter:$filter")
            player_view_mp.setFiler(0, mediaDuration, filter)
//            glFilterList.putGlFilter(GlFilterPeriod(0, mediaDuration, filter))
            filterConfigList.add(GlFilterConfig(filter.type, 0, mediaDuration))
        }
        dialogFragment.show(supportFragmentManager, "filter_dialog")
    }

    private fun getSelection() = PreferenceUtils.getInt(this, "filter_selection", 0)


}


