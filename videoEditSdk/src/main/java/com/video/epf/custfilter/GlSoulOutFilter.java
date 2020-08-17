package com.video.epf.custfilter;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;

import com.video.epf.R;
import com.video.epf.filter.FilterType;
import com.video.epf.filter.GlFilter;

import static com.video.library.util.GlUtil.raw;

public class GlSoulOutFilter extends GlFilter {

    float mScale = 0f;
    float mOffset = 0f;
    private int mScaleHandle;

    public GlSoulOutFilter(Context context) {
        super(context, R.raw.def_vertext, R.raw.fragment_soulout);
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.SPX_SOULOUT;
    }

    @Override
    public void initProgramHandle() {
        super.initProgramHandle();
        mScaleHandle = GLES30.glGetUniformLocation(mProgramHandle, "scale");
    }

    @Override
    public void onDraw() {
        mScale = 1.0f + 0.5f * getInterpolation(mOffset);
        mOffset += 0.04f;
        if (mOffset > 1.0f) {
            mOffset = 0.0f;
        }
        GLES20.glUniform1f(mScaleHandle, mScale);

    }

    private float getInterpolation(float input) {
        return (float) (Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
    }
}
