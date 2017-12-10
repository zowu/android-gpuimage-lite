/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView.Renderer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import li.imagefilter.LiGPUImageFilter;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

import static jp.co.cyberagent.android.gpuimage.util.TextureRotationUtil.TEXTURE_NO_ROTATION;

@TargetApi(11)
public class GPUImageRenderer implements Renderer {
    public static final int NO_IMAGE = -1;
    static final float CUBE[] = {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f,
    };

    private GPUImageFilter mFilter;

    public final Object mSurfaceChangedWaiter = new Object();

    private int mGLTextureId = NO_IMAGE;
    private SurfaceTexture mSurfaceTexture = null;
    private final FloatBuffer mGLCubeBuffer;
    private final FloatBuffer mGLTextureBuffer;

    private int mOutputWidth;
    private int mOutputHeight;
    private int mImageWidth;
    private int mImageHeight;

    private final Queue<Runnable> mRunOnDraw = new LinkedList<>();
    private final Queue<Runnable> mRunOnDrawEnd = new LinkedList<>();
    private float mScaleRatio = 1;
    private Rotation mRotation = Rotation.NORMAL;
    private float mRotationAngle;
    private Matrix transformMatrix = new Matrix();
    private float[] transformCenter = new float[2];
    private GPUImage.ScaleType mScaleType = GPUImage.ScaleType.CENTER_CROP;

    private float mBackgroundRed = 0;
    private float mBackgroundGreen = 0;
    private float mBackgroundBlue = 0;

    private float mCircleRadius = 0;
    private float mTransformCenterCords[] = new float[] {.5f, .5f};

    private float baseScaleRatioWidth = 1f;
    private float baseScaleRatioHeight = 1f;

    public GPUImageRenderer(@Nullable final GPUImageFilter filter) {
        mFilter = filter;
        mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
                                  .order(ByteOrder.nativeOrder())
                                  .asFloatBuffer();
        mGLCubeBuffer.put(CUBE).position(0);
        mGLTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_NO_ROTATION.length * 4)
                                     .order(ByteOrder.nativeOrder())
                                     .asFloatBuffer();
    }

    @Override
    public void onSurfaceCreated(@NonNull final GL10 unused, @NonNull final EGLConfig config) {
        GLES20.glClearColor(mBackgroundRed, mBackgroundGreen, mBackgroundBlue, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        mFilter.init();
    }

    @Override
    public void onSurfaceChanged(@NonNull final GL10 gl, final int width, final int height) {
        mOutputWidth = width;
        mOutputHeight = height;
        GLES20.glViewport(0, 0, width, height);
        GLES20.glUseProgram(mFilter.getProgram());
        mFilter.onOutputSizeChanged(width, height);
        initilizeTransformMatrix();
        adjustImageTransform();
        synchronized (mSurfaceChangedWaiter) {
            mSurfaceChangedWaiter.notifyAll();
        }
    }

    @Override
    public void onDrawFrame(@NonNull final GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        runAll(mRunOnDraw);
        mFilter.onDraw(mGLTextureId, mGLCubeBuffer, mGLTextureBuffer);
        runAll(mRunOnDrawEnd);
        if (mSurfaceTexture != null) {
            mSurfaceTexture.updateTexImage();
        }
    }

    /**
     * Sets the background color
     *
     * @param red   red color value
     * @param green green color value
     * @param blue  red color value
     */
    public void setBackgroundColor(float red, float green, float blue) {
        mBackgroundRed = red;
        mBackgroundGreen = green;
        mBackgroundBlue = blue;
    }

    private void runAll(@NonNull Queue<Runnable> queue) {
        synchronized (queue) {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    public void setFilter(@NonNull final GPUImageFilter filter) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                final GPUImageFilter oldFilter = mFilter;
                mFilter = filter;
                if (oldFilter != null) {
                    oldFilter.destroy();
                }
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
            }
        });
    }

    public void deleteImage() {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glDeleteTextures(1, new int[]{
                    mGLTextureId
                }, 0);
                mGLTextureId = NO_IMAGE;
            }
        });
    }

    public void setImageBitmap(@Nullable final Bitmap bitmap, final boolean recycle) {
        if (bitmap == null) {
            return;
        }

        runOnDraw(new Runnable() {

            @Override
            public void run() {
                Bitmap resizedBitmap = null;
                if (bitmap.getWidth() % 2 == 1) {
                    resizedBitmap = Bitmap.createBitmap(bitmap.getWidth() + 1, bitmap.getHeight(),
                                                        Bitmap.Config.ARGB_8888);
                    Canvas can = new Canvas(resizedBitmap);
                    can.drawARGB(0x00, 0x00, 0x00, 0x00);
                    can.drawBitmap(bitmap, 0, 0, null);
                }

                mGLTextureId = OpenGlUtils.loadTexture(
                    resizedBitmap != null ? resizedBitmap : bitmap, mGLTextureId, recycle);
                if (resizedBitmap != null) {
                    resizedBitmap.recycle();
                }
                mImageWidth = bitmap.getWidth();
                mImageHeight = bitmap.getHeight();
                initilizeTransformMatrix();
                adjustImageTransform();
            }
        });
    }

    public void setScaleType(@NonNull GPUImage.ScaleType scaleType) {
        mScaleType = scaleType;
    }

    protected int getFrameWidth() {
        return mOutputWidth;
    }

    protected int getFrameHeight() {
        return mOutputHeight;
    }

    private void initilizeTransformMatrix() {
        if (mCircleRadius == 0) {
            mCircleRadius = Math.min(mOutputWidth, mOutputHeight) / 2f;
        }

        // by default, the texture cords are mapped to output surface as [0,1] on both dimensions
        float ratio1 = mImageWidth / (float) mOutputWidth;
        float ratio2 = mImageHeight / (float) mOutputHeight;

        if (mImageWidth >= mImageHeight) {
            baseScaleRatioHeight = (float) mOutputHeight / (mCircleRadius * 2);
            baseScaleRatioWidth = baseScaleRatioHeight * ratio2 / ratio1;
        } else {
            baseScaleRatioWidth = (float) mOutputWidth / (mCircleRadius * 2);
            baseScaleRatioHeight = baseScaleRatioWidth * ratio1 / ratio2;
        }

        mRotationAngle = 0f;
        mRotation = Rotation.NORMAL;
        mScaleRatio = 1f;
        transformMatrix = new Matrix();
        transformMatrix.setScale(baseScaleRatioWidth, baseScaleRatioHeight, .5f, .5f);
    }

    private boolean adjustImageTransform() {
        float[] cube = CUBE;
        float[] textureCords = new float[8];
        float[] transformCenter = new float[2];

        transformMatrix.mapPoints(textureCords, TEXTURE_NO_ROTATION);
        transformMatrix.mapPoints(transformCenter, mTransformCenterCords);

        // edge detection, adjust boundaries to keep the whole image within circle
        float imageWidthInPixel = mOutputWidth / baseScaleRatioWidth / mScaleRatio;
        float imageHeightInPixel = mOutputHeight / baseScaleRatioHeight / mScaleRatio;
        if(transformCenter[0] < mCircleRadius / imageWidthInPixel ) {
            transformMatrix.postTranslate(mCircleRadius / imageWidthInPixel - transformCenter[0], 0);
        } else if (1 - transformCenter[0] < mCircleRadius / imageWidthInPixel) {
            transformMatrix.postTranslate(1 - transformCenter[0] - mCircleRadius / imageWidthInPixel, 0);
        }

        if(transformCenter[1] < mCircleRadius / imageHeightInPixel) {
            transformMatrix.postTranslate(0, mCircleRadius / imageHeightInPixel - transformCenter[1]);
        } else if (1 - transformCenter[1] < mCircleRadius / imageHeightInPixel) {
            transformMatrix.postTranslate(0, 1 - transformCenter[1] - mCircleRadius / imageHeightInPixel);
        }

        // re-map the texture cords after boundary adjustments
        transformMatrix.mapPoints(textureCords, TEXTURE_NO_ROTATION);
        transformMatrix.mapPoints(transformCenter, mTransformCenterCords);

        this.transformCenter = transformCenter;

        mGLCubeBuffer.clear();
        mGLCubeBuffer.put(cube).position(0);
        mGLTextureBuffer.clear();
        mGLTextureBuffer.put(textureCords).position(0);
        return true;
    }

    public void setScaleFactor(float scaleFactor) {
        float newScaleRatio = mScaleRatio / scaleFactor;
        if (newScaleRatio >= 0.1 && newScaleRatio <= 1) {
            transformMatrix.postScale(1f/scaleFactor, 1f/scaleFactor, transformCenter[0], transformCenter[1]);
            mScaleRatio = newScaleRatio;
            adjustImageTransform();
        }
    }

    public void setTranslate(float x, float y) {
        float[] transformedTranslate = new float[]{x / mOutputWidth * mScaleRatio, y / mOutputHeight * mScaleRatio};
        Matrix matrix = new Matrix();
        matrix.postRotate(mRotation.asInt() + mRotationAngle);
        matrix.mapPoints(transformedTranslate);
        transformMatrix.postTranslate(transformedTranslate[0], transformedTranslate[1]);
        adjustImageTransform();
    }

    public PointF getCropTopLeft() {
        return getCenterCoordinatesWithOffset(-mCircleRadius, -mCircleRadius);
    }

    public PointF getCropTopRight() {
        return getCenterCoordinatesWithOffset(mCircleRadius, -mCircleRadius);
    }

    public PointF getCropBottomLeft() {
        return getCenterCoordinatesWithOffset(-mCircleRadius, mCircleRadius);
    }

    public PointF getCropBottomRight() {
        return getCenterCoordinatesWithOffset(mCircleRadius, mCircleRadius);
    }

    // get the coordinates of a point with (offsetX, offsetY) from the transformCenter
    private PointF getCenterCoordinatesWithOffset(float offsetX, float offsetY) {
        float[] coords = new float[]{mTransformCenterCords[0] + offsetX / mOutputWidth, mTransformCenterCords[1] + offsetY / mOutputHeight};
        transformMatrix.mapPoints(coords);
        return new PointF(coords[0], coords[1]);
    }

    public void setCropRectangle(@NonNull PointF topLeft,
                                 @NonNull PointF topRight,
                                 @NonNull PointF bottomLeft,
                                 @NonNull PointF bottomRight) {
        Matrix pointsOriginal = new Matrix();
        pointsOriginal.setValues(new float[]{
            mTransformCenterCords[0] - mCircleRadius / mOutputWidth,
            mTransformCenterCords[0] + mCircleRadius / mOutputWidth,
            mTransformCenterCords[0] - mCircleRadius / mOutputWidth,
            mTransformCenterCords[1] - mCircleRadius / mOutputHeight,
            mTransformCenterCords[1] - mCircleRadius / mOutputHeight,
            mTransformCenterCords[1] + mCircleRadius / mOutputHeight,
            1, 1, 1});
        pointsOriginal.invert(pointsOriginal);
        Matrix pointsAfterTransform = new Matrix();
        pointsAfterTransform.setValues(new float[]{
            topLeft.x, topRight.x, bottomLeft.x,
            topLeft.y, topRight.y, bottomLeft.y,
            1, 1, 1});
        transformMatrix.setConcat(pointsAfterTransform, pointsOriginal);

        float[] p1 = new float[]{0, 0};
        float[] p2 = new float[]{1, 0};
        transformMatrix.mapPoints(p1);
        transformMatrix.mapPoints(p2);
        float diffX = (p2[1] - p1[1])*mOutputHeight/baseScaleRatioHeight;
        float diffY = (p2[0] - p1[0])*mOutputWidth/baseScaleRatioWidth;
        float angle = (float) Math.toDegrees(Math.atan2(diffX, diffY));
        mRotationAngle = ( angle + 225 ) % 90 - 45;
        switch ((((int)angle + 405)/ 90) % 4) {
            case 1:
                mRotation = Rotation.ROTATION_90;
                break;
            case 2:
                mRotation = Rotation.ROTATION_180;
                break;
            case 3:
                mRotation = Rotation.ROTATION_270;
                break;
            case 0:
            default:
                mRotation = Rotation.NORMAL;
                break;
        }
        mScaleRatio = (float) Math.sqrt(diffX * diffX + diffY * diffY) / mOutputWidth;
        adjustImageTransform();
    }

    public void setTransformOffsetLimit(float leftOffset, float rightOffset, float topOffset, float bottomOffset, float outputWidth, float outputHeight) {
        mTransformCenterCords[0] = .5f + .5f * (leftOffset - rightOffset) / outputWidth;
        mTransformCenterCords[1] = .5f + .5f * (topOffset - bottomOffset) / outputHeight;

        mCircleRadius = (outputWidth - leftOffset - rightOffset) / 2;
    }

    public void setRotationAngle(final float rotationAngle) {
        Matrix matrix = new Matrix();
        matrix.setScale(1f, mImageHeight/(float)mImageWidth, transformCenter[0], transformCenter[1]);
        matrix.postRotate(rotationAngle - mRotationAngle, transformCenter[0], transformCenter[1]);
        matrix.postScale(1f, mImageWidth/(float)mImageHeight, transformCenter[0], transformCenter[1]);

        transformMatrix.postConcat(matrix);
        adjustImageTransform();
        mRotationAngle = rotationAngle;
    }

    public float getRotationAngle() {
        return mRotationAngle;
    }

    public void rotate(boolean clockwise) {
        setRotation(clockwise ? mRotation.clockwiseNext() : mRotation.counterClockwiseNext());
    }

    public void setRotation(final Rotation rotation) {
        Matrix matrix = new Matrix();
        matrix.setScale(1f, mImageHeight/(float)mImageWidth, transformCenter[0], transformCenter[1]);
        matrix.postRotate(rotation.asInt() - mRotation.asInt(), transformCenter[0], transformCenter[1]);
        matrix.postScale(1f, mImageWidth/(float)mImageHeight, transformCenter[0], transformCenter[1]);

        transformMatrix.postConcat(matrix);
        adjustImageTransform();
        mRotation = rotation;
    }

    protected void runOnDraw(@NonNull final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.add(runnable);
        }
    }

    protected void runOnDrawEnd(@NonNull final Runnable runnable) {
        synchronized (mRunOnDrawEnd) {
            mRunOnDrawEnd.add(runnable);
        }
    }
}
