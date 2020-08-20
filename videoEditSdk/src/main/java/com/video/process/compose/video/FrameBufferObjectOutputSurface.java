package com.video.process.compose.video;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;

import com.video.process.utils.LogUtils;
import com.video.process.preview.EFramebufferObject;
import com.video.process.preview.EglUtil;
import com.video.process.preview.filter.GlFilter;
import com.video.process.utils.GLESUtils;

import java.util.HashMap;
import java.util.Map;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;

public abstract class FrameBufferObjectOutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "FBOOutputSurface";
    private static final int PBO_SIZE = 2;
    private EFramebufferObject mFramebufferObject;
    private EFramebufferObject mLastFrameFBO;
    private GlFilter mNormalShader;
    protected SurfaceTexture mSurfaceTexture;

    private int[] mPboIds = new int[PBO_SIZE];
    private int mLastPboId = -1;
    private int mWidth, mHeight;

    private Map<String, Integer> mExtraTextureIds = new HashMap<>();

    public final void setupAll() {
        mWidth = getOutputWidth();
        mHeight = getOutputHeight();
        mFramebufferObject = new EFramebufferObject();
        mLastFrameFBO = new EFramebufferObject();
        mNormalShader = new GlFilter();
        mNormalShader.setup();

        mFramebufferObject.setup(mWidth, mHeight);
        mLastFrameFBO.setup(mWidth, mHeight);
        mNormalShader.setFrameSize(mWidth, mHeight);

        mPboIds = EglUtil.genPbo(PBO_SIZE, mWidth, mHeight);

        setup();
    }

    protected abstract int getOutputHeight();

    protected abstract int getOutputWidth();

    protected abstract void setup();

    private Object frameSyncObject = new Object();     // guards frameAvailable
    private boolean frameAvailable;
    private volatile boolean stopRun = false;

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        LogUtils.d(TAG + ", new frame available");
        synchronized (frameSyncObject) {
            if (frameAvailable) {
                throw new RuntimeException("frameAvailable already set, frame could be dropped");
            }
            frameAvailable = true;
            frameSyncObject.notifyAll();
        }
    }

    public void stopRun() {
        stopRun = true;
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the DecoderSurface object, after the onFrameAvailable callback has signaled that new
     * data is available.
     */
    public void awaitNewImage() {
        final int TIMEOUT_MS = 10000;
        synchronized (frameSyncObject) {
            while (!frameAvailable && !stopRun) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    frameSyncObject.wait(TIMEOUT_MS);
                    if (!frameAvailable && !stopRun) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw new RuntimeException("Surface frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
            frameAvailable = false;
        }
        if (stopRun) {
            return;
        }
        // Latch the data.
        GLESUtils.checkGlError("before updateTexImage");
        mSurfaceTexture.updateTexImage();
    }


    public void drawImage(long presentationTimeUs) {
        LogUtils.d(TAG + ", drawImage: presentationTimeUs:" + presentationTimeUs);
        mFramebufferObject.enable();
        GLES20.glViewport(0, 0, mFramebufferObject.getWidth(), mFramebufferObject.getHeight());

        onDrawFrame(mFramebufferObject, presentationTimeUs, mExtraTextureIds);

        // 在最外层, 最终把输出从屏幕输出
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mFramebufferObject.getWidth(), mFramebufferObject.getHeight());
        GLES20.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        mNormalShader.draw(mFramebufferObject.getTexName(), null, null);

        if (needLastFrame()) {
            // 先绘制到上一帧fbo中.
            mLastFrameFBO.enable();
            GLES20.glViewport(0, 0, mFramebufferObject.getWidth(), mFramebufferObject.getHeight());
            GLES20.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            mNormalShader.draw(mFramebufferObject.getTexName(), null, null);
            int lastTexName = mLastFrameFBO.getTexName();
            mExtraTextureIds.put("last_frame_texture", lastTexName);
        }
    }

    protected boolean needLastFrame() {
        return false;
    }

    public abstract void onDrawFrame(EFramebufferObject framebufferObject, long presentationTimeUs, Map<String, Integer> extraTextureIds);

    public int getLastTextId(int offset) {
        return mLastPboId;
    }
}
