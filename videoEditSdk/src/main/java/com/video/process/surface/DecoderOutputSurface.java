package com.video.process.surface;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Surface;

import com.video.process.model.VideoSize;
import com.video.process.preview.filter.GlFilterList;
import com.video.process.utils.LogUtils;
import com.video.process.preview.EFramebufferObject;
import com.video.process.preview.EglUtil;
import com.video.process.preview.filter.GlFilter;
import com.video.process.preview.filter.GlPreviewFilter;
import com.video.process.model.FillMode;
import com.video.process.model.CustomFillMode;
import com.video.process.model.Rotation;
import com.video.process.compose.video.FrameBufferObjectOutputSurface;

import java.util.Map;

import static android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.glViewport;

public class DecoderOutputSurface extends FrameBufferObjectOutputSurface {
    private static final String TAG = "DecoderSurface";
    private Surface mSurface;

    private float[] MVPMatrix = new float[16];
    private float[] STMatrix = new float[16];
    private float[] ProjMatrix = new float[16];
    private float[] MMatrix = new float[16];
    private float[] VMatrix = new float[16];

    private Rotation mRotation = Rotation.NORMAL;
    private VideoSize mOutputVideoSize;
    private VideoSize mInputVideoSize;
    private FillMode mFillMode = FillMode.PRESERVE_ASPECT_FIT;
    private CustomFillMode mCustomFillMode;
    private boolean mFlipVertical = false;
    private boolean mFlipHorizontal = false;
    private int mTextureID = -12345;

    private GlFilter mFilter;
    private GlFilterList mFilterList;
    private EFramebufferObject mFilterFrameBuffer;

    private GlPreviewFilter mPreviewFilter;

    private boolean mIsNewFilter;

    /**
     * Creates an DecoderSurface using the current EGL context (rather than establishing a
     * new one).  Creates a Surface that can be passed to MediaCodec.configure().
     */
    public DecoderOutputSurface(GlFilter filter, GlFilterList filterList) {
        mFilter = filter;
        mFilterList = filterList;
        if (filterList != null) {
            mIsNewFilter = true;
        }
    }

    public void setRotation(Rotation rotation) {
        mRotation = rotation;
    }

    public void setOutputVideoSize(VideoSize videoSize) {
        mOutputVideoSize = videoSize;
    }

    public void setFillMode(FillMode fillMode) {
        mFillMode = fillMode;
    }

    public void setInputResolution(VideoSize videoSize) {
        mInputVideoSize = videoSize;
    }

    public void setFillModeCustomItem(CustomFillMode fillModeCustomItem) {
        mCustomFillMode = fillModeCustomItem;
    }

    public void setFlipVertical(boolean flipVertical) {
        mFlipVertical = flipVertical;
    }

    public void setFlipHorizontal(boolean flipHorizontal) {
        mFlipHorizontal = flipHorizontal;
    }

    @Override
    protected int getOutputHeight() {
        return mOutputVideoSize.mHeight;
    }

    @Override
    protected int getOutputWidth() {
        return mOutputVideoSize.mWidth;
    }

    /**
     * Creates instances of TextureRender and SurfaceTexture, and a Surface associated
     * with the SurfaceTexture.
     */
    public void setup() {
        int width = mOutputVideoSize.mWidth;
        int height = mOutputVideoSize.mHeight;
        LogUtils.d(TAG + ", setup: width:" + width + ", height:" + height);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mTextureID);
        // GL_TEXTURE_EXTERNAL_OES
        EglUtil.setupSampler(GL_TEXTURE_EXTERNAL_OES, GL_LINEAR, GL_NEAREST);
        GLES20.glBindTexture(GL_TEXTURE_2D, 0);


        mFilterFrameBuffer = new EFramebufferObject();
        mFilterFrameBuffer.setup(mOutputVideoSize.mWidth, mOutputVideoSize.mHeight);

        mPreviewFilter = new GlPreviewFilter(GL_TEXTURE_EXTERNAL_OES);
        mPreviewFilter.setup();

        // GL_TEXTURE_EXTERNAL_OES
        // Even if we don't access the SurfaceTexture after the constructor returns, we
        // still need to keep a reference to it.  The Surface doesn't retain a reference
        // at the Java level, so if we don't either then the object can get GCed, which
        // causes the native finalizer to run.
        LogUtils.d(TAG + ", textureID=" + mTextureID);
        mSurfaceTexture = new SurfaceTexture(mTextureID);
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
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mSurface = new Surface(mSurfaceTexture);

        Matrix.setIdentityM(STMatrix, 0);
    }


    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
        mSurface.release();
        if (mFilterList != null) {
            mFilterList.release();
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
        mSurface = null;
        mSurfaceTexture = null;
    }

    /**
     * Returns the Surface that we draw onto.
     */
    public Surface getSurface() {
        return mSurface;
    }


    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     *
     * @param presentationTimeUs
     * @param extraTextureIds
     */
    public void onDrawFrame(EFramebufferObject fbo, long presentationTimeUs, Map<String, Integer> extraTextureIds) {

        Matrix.setIdentityM(MVPMatrix, 0);

        float scaleDirectionX = mFlipHorizontal ? -1 : 1;
        float scaleDirectionY = mFlipVertical ? -1 : 1;

        if (mIsNewFilter) {
            if (mFilterList != null) {
                mFilterList.setFrameSize(fbo.getWidth(), fbo.getHeight());
            }
            mIsNewFilter = false;
        }

        float scale[];
        switch (mFillMode) {
            case PRESERVE_ASPECT_FIT:
                scale = FillMode.getScaleAspectFit(mRotation.getRotation(), mInputVideoSize.mWidth, mInputVideoSize.mHeight, mOutputVideoSize.mWidth, mOutputVideoSize.mHeight);
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1);
                if (mRotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, -mRotation.getRotation(), 0.f, 0.f, 1.f);
                }
                break;
            case PRESERVE_ASPECT_CROP:
                scale = FillMode.getScaleAspectCrop(mRotation.getRotation(), mInputVideoSize.mWidth, mInputVideoSize.mHeight, mOutputVideoSize.mWidth, mOutputVideoSize.mHeight);
                Matrix.scaleM(MVPMatrix, 0, scale[0] * scaleDirectionX, scale[1] * scaleDirectionY, 1);
                if (mRotation != Rotation.NORMAL) {
                    Matrix.rotateM(MVPMatrix, 0, -mRotation.getRotation(), 0.f, 0.f, 1.f);
                }
                break;
            case CUSTOM:
                if (mCustomFillMode != null) {
                    Matrix.translateM(MVPMatrix, 0, mCustomFillMode.getTranslateX(), -mCustomFillMode.getTranslateY(), 0f);
                    scale = FillMode.getScaleAspectCrop(mRotation.getRotation(), mInputVideoSize.mWidth, mInputVideoSize.mHeight, mOutputVideoSize.mWidth, mOutputVideoSize.mHeight);
                    if (mCustomFillMode.getRotate() == 0 || mCustomFillMode.getRotate() == 180) {
                        Matrix.scaleM(MVPMatrix,
                                0,
                                mCustomFillMode.getScale() * scale[0] * scaleDirectionX,
                                mCustomFillMode.getScale() * scale[1] * scaleDirectionY,
                                1);
                    } else {
                        Matrix.scaleM(MVPMatrix,
                                0,
                                mCustomFillMode.getScale() * scale[0] * (1 / mCustomFillMode.getVideoWidth() * mCustomFillMode.getVideoHeight()) * scaleDirectionX,
                                mCustomFillMode.getScale() * scale[1] * (mCustomFillMode.getVideoWidth() / mCustomFillMode.getVideoHeight()) * scaleDirectionY,
                                1);
                    }

                    Matrix.rotateM(MVPMatrix, 0, -(mRotation.getRotation() + mCustomFillMode.getRotate()), 0.f, 0.f, 1.f);
                }
            default:
                break;
        }
        LogUtils.d(TAG + ", onDrawFrame: ...filterList:"+mFilterList);
        if (mFilterList != null) {
            mFilterFrameBuffer.enable();
            glViewport(0, 0, mFilterFrameBuffer.getWidth(), mFilterFrameBuffer.getHeight());
        }
        mSurfaceTexture.getTransformMatrix(STMatrix);

        // 这句绘制的目的地是哪?  --如果glFilterFrameBuffer没有启用, 那就fbo, 否则就是glFilterFrameBuffer
        mPreviewFilter.draw(mTextureID, MVPMatrix, STMatrix, 1.0f);

        if (mFilterList != null) {
            fbo.enable();  // 重新启用了最外层的fbo , 那么glFilter的输出就到了这个fbo .
            GLES20.glClear(GL_COLOR_BUFFER_BIT);
            mFilterList.draw(mFilterFrameBuffer.getTexName(), fbo, presentationTimeUs, extraTextureIds);
        }
    }

    protected boolean needLastFrame() {
        if (mFilterList != null) {
            return mFilterList.needLastFrame();
        }
        return false;
    }
}

