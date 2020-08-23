package com.video.process.surface;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.video.process.model.VideoSize;
import com.video.process.preview.EFramebufferObject;
import com.video.process.preview.filter.GlFilter;
import com.video.process.model.FillMode;
import com.video.process.model.CustomFillMode;
import com.video.process.model.Rotation;
import com.video.process.compose.filter.GlComposeFilter;
import com.video.process.utils.GLESUtils;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAMEBUFFER;

public class DecoderSurface2 implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "DecoderSurface";
    private static final boolean VERBOSE = false;

    private EFramebufferObject framebufferObject;
    private GlFilter normalShader;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private Object frameSyncObject = new Object();     // guards frameAvailable
    private boolean frameAvailable;
    private GlComposeFilter filter;

    private float[] MVPMatrix = new float[16];
    private float[] STMatrix = new float[16];

    private Rotation rotation = Rotation.NORMAL;
    private VideoSize outputResolution;
    private VideoSize inputResolution;
    private FillMode fillMode = FillMode.PRESERVE_ASPECT_FIT;
    private CustomFillMode fillModeCustomItem;
    private boolean flipVertical = false;
    private boolean flipHorizontal = false;

    /**
     * Creates an DecoderSurface using the current EGL context (rather than establishing a
     * new one).  Creates a Surface that can be passed to MediaCodec.configure().
     */
    DecoderSurface2(GlComposeFilter filter) {

        framebufferObject = new EFramebufferObject();
        normalShader = new GlFilter();
        normalShader.setup();

        framebufferObject.setup(320, 480);
        normalShader.setFrameSize(320, 480);


        this.filter = filter;
        this.filter.setUpSurface();
        setup();
    }

    /**
     * Creates instances of TextureRender and SurfaceTexture, and a Surface associated
     * with the SurfaceTexture.
     */
    private void setup() {

        // Even if we don't access the SurfaceTexture after the constructor returns, we
        // still need to keep a reference to it.  The Surface doesn't retain a reference
        // at the Java level, so if we don't either then the object can get GCed, which
        // causes the native finalizer to run.
        if (VERBOSE) Log.d(TAG, "textureID=" + filter.getTextureId());
        surfaceTexture = new SurfaceTexture(filter.getTextureId());
        // This doesn't work if DecoderSurface is created on the thread that CTS started for
        // these test cases.
        //
        // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
        // create a Handler that uses it.  The "frame available" message is delivered
        // there, but since we're not a Looper-based thread we'll never see it.  For
        // this to do anything useful, DecoderSurface must be created on a thread without
        // a Looper, so that SurfaceTexture uses the main application Looper instead.
        //
        // Java language note: passing "this" out of a constructor is generally unwise,
        // but we should be able to get away with it here.
        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);

        Matrix.setIdentityM(STMatrix, 0);
    }


    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    void release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }
        surface.release();
        // this causes a bunch of warnings that appear harmless but might confuse someone:
        //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        //surfaceTexture.release();
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
        filter.release();
        filter = null;
        surface = null;
        surfaceTexture = null;
    }

    /**
     * Returns the Surface that we draw onto.
     */
    Surface getSurface() {
        return surface;
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the DecoderSurface object, after the onFrameAvailable callback has signaled that new
     * data is available.
     */
    void awaitNewImage() {
        final int TIMEOUT_MS = 10000;
        synchronized (frameSyncObject) {
            while (!frameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    frameSyncObject.wait(TIMEOUT_MS);
                    if (!frameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw new RuntimeException("Surface frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            frameAvailable = false;
        }
        // Latch the data.
        GLESUtils.checkGlError("before updateTexImage");
        surfaceTexture.updateTexImage();
    }


    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     * @param presentationTimeUs
     */
    void drawImage(long presentationTimeUs) {

        Matrix.setIdentityM(MVPMatrix, 0);

        float scaleDirectionX = flipHorizontal ? -1 : 1;
        float scaleDirectionY = flipVertical ? -1 : 1;


        float scale[];
        switch (fillMode) {
            case PRESERVE_ASPECT_FIT:
                scale = FillMode.getScaleAspectFit(rotation.getRotation(), inputResolution.mWidth, inputResolution.mHeight, outputResolution.mWidth, outputResolution.mHeight);
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1);
                if (rotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, -rotation.getRotation(), 0.f, 0.f, 1.f);
                }
                break;
            case PRESERVE_ASPECT_CROP:
                scale = FillMode.getScaleAspectCrop(rotation.getRotation(), inputResolution.mWidth, inputResolution.mHeight, outputResolution.mWidth, outputResolution.mHeight);
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1);
                if (rotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, -rotation.getRotation(), 0.f, 0.f, 1.f);
                }
                break;
            case CUSTOM:
                if (fillModeCustomItem != null) {
                    Matrix.translateM(MVPMatrix, 0, fillModeCustomItem.getTranslateX(), -fillModeCustomItem.getTranslateY(), 0f);
                    scale = FillMode.getScaleAspectCrop(rotation.getRotation(), inputResolution.mWidth, inputResolution.mHeight, outputResolution.mWidth, outputResolution.mHeight);

                    if (fillModeCustomItem.getRotate() == 0 || fillModeCustomItem.getRotate() == 180) {
                        Matrix.scaleM(MVPMatrix,
                                0,
                                fillModeCustomItem.getScale() * scale[0] * scaleDirectionX,
                                fillModeCustomItem.getScale() * scale[1] * scaleDirectionY,
                                1);
                    } else {
                        Matrix.scaleM(MVPMatrix,
                                0,
                                fillModeCustomItem.getScale() * scale[0] * (1 / fillModeCustomItem.getVideoWidth() * fillModeCustomItem.getVideoHeight()) * scaleDirectionX,
                                fillModeCustomItem.getScale() * scale[1] * (fillModeCustomItem.getVideoWidth() / fillModeCustomItem.getVideoHeight()) * scaleDirectionY,
                                1);
                    }

                    Matrix.rotateM(MVPMatrix, 0, -(rotation.getRotation() + fillModeCustomItem.getRotate()), 0.f, 0.f, 1.f);
                }
            default:
                break;
        }


        framebufferObject.enable();
        GLES20.glViewport(0, 0, framebufferObject.getWidth(), framebufferObject.getHeight());

        filter.draw(surfaceTexture, STMatrix, MVPMatrix);

        // 在最外层, 最终把输出从屏幕输出
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, framebufferObject.getWidth(), framebufferObject.getHeight());

        GLES20.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        normalShader.draw(framebufferObject.getTexName(), null, null);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (VERBOSE) Log.d(TAG, "new frame available");
        synchronized (frameSyncObject) {
            if (frameAvailable) {
                throw new RuntimeException("frameAvailable already set, frame could be dropped");
            }
            frameAvailable = true;
            frameSyncObject.notifyAll();
        }
    }

    void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }


    void setOutputResolution(VideoSize resolution) {
        this.outputResolution = resolution;
    }

    void setFillMode(FillMode fillMode) {
        this.fillMode = fillMode;
    }

    void setInputResolution(VideoSize resolution) {
        this.inputResolution = resolution;
    }

    void setFillModeCustomItem(CustomFillMode fillModeCustomItem) {
        this.fillModeCustomItem = fillModeCustomItem;
    }

    public void setFlipVertical(boolean flipVertical) {
        this.flipVertical = flipVertical;
    }

    public void setFlipHorizontal(boolean flipHorizontal) {
        this.flipHorizontal = flipHorizontal;
    }
}
