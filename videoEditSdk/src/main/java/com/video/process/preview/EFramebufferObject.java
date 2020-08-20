package com.video.process.preview;

import android.opengl.GLES20;
import android.util.Log;

import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.GL_DEPTH_ATTACHMENT;
import static android.opengl.GLES20.GL_DEPTH_COMPONENT16;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.GL_FRAMEBUFFER_COMPLETE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MAX_RENDERBUFFER_SIZE;
import static android.opengl.GLES20.GL_MAX_TEXTURE_SIZE;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_RENDERBUFFER;
import static android.opengl.GLES20.GL_RENDERBUFFER_BINDING;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_BINDING_2D;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;

public class EFramebufferObject {

    private static final String TAG = "EFramebufferObject";
    private int mWidth;
    private int mHeight;
    public int mFramebufferName;
    private int mRenderbufferName;
    private int mTexName;

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getTexName() {
        return mTexName;
    }

    public void setup(final int width, final int height) {
        final int[] args = new int[1];
        Log.d(TAG, "setup: ....");
        GLES20.glGetIntegerv(GL_MAX_TEXTURE_SIZE, args, 0);
        if (width > args[0] || height > args[0]) {
            throw new IllegalArgumentException("GL_MAX_TEXTURE_SIZE " + args[0]);
        }

        GLES20.glGetIntegerv(GL_MAX_RENDERBUFFER_SIZE, args, 0);
        if (width > args[0] || height > args[0]) {
            throw new IllegalArgumentException("GL_MAX_RENDERBUFFER_SIZE " + args[0]);
        }

        GLES20.glGetIntegerv(GL_FRAMEBUFFER_BINDING, args, 0);
        final int saveFramebuffer = args[0];
        GLES20.glGetIntegerv(GL_RENDERBUFFER_BINDING, args, 0);
        final int saveRenderbuffer = args[0];
        GLES20.glGetIntegerv(GL_TEXTURE_BINDING_2D, args, 0);
        final int saveTexName = args[0];
        Log.d(TAG, "setup: saveFramebuffer:"+saveFramebuffer+", saveTexName:"+saveTexName);

        release();

        try {
            this.mWidth = width;
            this.mHeight = height;

            GLES20.glGenFramebuffers(args.length, args, 0);
            mFramebufferName = args[0];
            GLES20.glBindFramebuffer(GL_FRAMEBUFFER, mFramebufferName);

            GLES20.glGenRenderbuffers(args.length, args, 0);
            mRenderbufferName = args[0];
            GLES20.glBindRenderbuffer(GL_RENDERBUFFER, mRenderbufferName);
            GLES20.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, width, height);
            GLES20.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, mRenderbufferName);

            GLES20.glGenTextures(args.length, args, 0);
            mTexName = args[0];  // 这个纹理作为framebuffer的颜色缓冲区(GL_COLOR_ATTACHMENT0), 也就是视频流输出
            GLES20.glBindTexture(GL_TEXTURE_2D, mTexName);

            EglUtil.setupSampler(GL_TEXTURE_2D, GL_LINEAR, GL_NEAREST);

            GLES20.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            GLES20.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mTexName, 0);

            final int status = GLES20.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Failed to initialize framebuffer object " + status);
            }
        } catch (final RuntimeException e) {
            release();
            throw e;
        }

        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, saveFramebuffer);
        GLES20.glBindRenderbuffer(GL_RENDERBUFFER, saveRenderbuffer);
        GLES20.glBindTexture(GL_TEXTURE_2D, saveTexName);
    }

    public void release() {
        final int[] args = new int[1];
        args[0] = mTexName;
        GLES20.glDeleteTextures(args.length, args, 0);
        mTexName = 0;
        args[0] = mRenderbufferName;
        GLES20.glDeleteRenderbuffers(args.length, args, 0);
        mRenderbufferName = 0;
        args[0] = mFramebufferName;
        GLES20.glDeleteFramebuffers(args.length, args, 0);
        mFramebufferName = 0;
    }

    public void enable() {
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, mFramebufferName);
    }


}
