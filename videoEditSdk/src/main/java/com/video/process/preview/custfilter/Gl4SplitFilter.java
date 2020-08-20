package com.video.process.preview.custfilter;

import android.content.Context;

import com.video.process.R;
import com.video.process.preview.filter.FilterType;
import com.video.process.preview.filter.GlFilter;

public class Gl4SplitFilter extends GlFilter {


    public Gl4SplitFilter(Context context) {
        super(context, R.raw.def_vertext, R.raw.fragment_split4);
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.SPX_4SPLIT;
    }
}


