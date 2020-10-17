package com.video.process.surface;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.video.process.utils.LogUtils;

public class InputSurface {
    private static final String TAG = "InputSurface";

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int EGL_OPENGL_ES2_BIT = 4;

    private Surface mSurface;
    private EGLDisplay mEGLDisplay;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;

    public InputSurface(@NonNull Surface surface) {
        mSurface = surface;
        setUpGglEnv();
    }

    private void setUpGglEnv() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            LogUtils.e(TAG, "unable to get EGL14 display");
            return;
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            LogUtils.e(TAG, "unable to initialize EGL14");
            return;
        }
        int[] attribList = {EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE};
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0,
                configs.length, numConfigs, 0)) {
            LogUtils.e(TAG, "unable to find RGB888+recordable ES2 EGL config");
            return;
        }
        int[] attrib_list = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};

        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0],
                EGL14.EGL_NO_CONTEXT, attrib_list, 0);
        checkEglError();
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        int[] surfaceAttribs = {EGL14.EGL_NONE};
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0],
                mSurface, surfaceAttribs, 0);

        checkEglError();
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }

    }

    private void checkEglError() {
        boolean failed = false;
        while (EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("EGL error encountered (see log)");
        }
    }

    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface,
                mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    public boolean swapBuffers() {
        return EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    public void release() {
        if (EGL14.eglGetCurrentContext().equals(mEGLContext)) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        }
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
        mSurface.release();
        mEGLDisplay = null;
        mEGLContext = null;
        mEGLSurface = null;
        mSurface = null;
    }

    public void setPresentationTime(long presentationTime) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, presentationTime);
    }
}
