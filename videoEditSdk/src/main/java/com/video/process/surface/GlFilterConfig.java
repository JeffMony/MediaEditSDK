package com.video.process.surface;

import com.video.process.preview.filter.FilterType;

import java.io.Serializable;

public class GlFilterConfig implements Serializable {
    public FilterType filterName;
    public long startTimeMs;
    public long endTimeMs;

    public GlFilterConfig(FilterType filterName, long startTimeMs, long endTimeMs) {
        this.filterName = filterName;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
    }
}
