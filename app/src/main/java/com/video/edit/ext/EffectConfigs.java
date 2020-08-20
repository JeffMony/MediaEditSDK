package com.video.edit.ext;

import android.content.Context;

import com.video.edit.demo.R;
import com.video.edit.view.BottomDialogFragment;
import com.video.process.surface.GLImageComplexionBeautyFilter;
import com.video.process.preview.custfilter.Gl4SplitFilter;
import com.video.process.preview.custfilter.GlFlashFliter;
import com.video.process.preview.custfilter.GlHuanJueFliter;
import com.video.process.preview.custfilter.GlItchFilter;
import com.video.process.preview.custfilter.GlScaleFilter;
import com.video.process.preview.custfilter.GlShakeFilter;
import com.video.process.preview.custfilter.GlSoulOutFilter;
import com.video.process.preview.filter.GlFilter;

import java.util.ArrayList;
import java.util.List;

public class EffectConfigs {

    public static List<BottomDialogFragment.Option> createEffectOptions() {
        List<BottomDialogFragment.Option> result = new ArrayList<>();
        result.add(new BottomDialogFragment.Option(R.drawable.ic_beauty_no, "无特效", 0));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_langman, "灵魂出窍", 1));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_rixi, "幻觉", 2));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_qingliang, "闪电", 3));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_langman, "毛刺", 4));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_langman, "缩放", 5));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_langman, "抖动", 6));
        result.add(new BottomDialogFragment.Option(R.drawable.ic_filter_langman, "四分镜", 7));
        return result;
    }

    public static GlFilter getEffectFilterByName(String name, Context context) {
        if (name.equals("无特效")) {
            return new GlFilter();
        } else if (name.equals("缩放")) {
            return new GlScaleFilter(context);
        } else if (name.equals("抖动")) {
            return new GlShakeFilter(context);
        } else if (name.equals("四分镜")) {
            return new Gl4SplitFilter(context);
        } else if (name.equals("灵魂出窍")) {
            return new GlSoulOutFilter(context);
        } else if (name.equals("幻觉")) {
            return new GlHuanJueFliter(context);
        } else if (name.equals("闪电")) {
            return new GlFlashFliter(context);
        } else if (name.equals("毛刺")) {
            return new GlItchFilter(context);
        } else {
            return new GLImageComplexionBeautyFilter(context);
        }
    }
}
