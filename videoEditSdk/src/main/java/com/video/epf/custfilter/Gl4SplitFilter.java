package com.video.epf.custfilter;

import android.content.Context;

import com.video.epf.R;
import com.video.epf.filter.FilterType;
import com.video.epf.filter.GlFilter;

public class Gl4SplitFilter extends GlFilter {


    public Gl4SplitFilter(Context context) {
        super(context, R.raw.def_vertext, R.raw.fragment_split4);
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.SPX_4SPLIT;
    }
}


