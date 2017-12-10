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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * The main accessor for GPUImage functionality. This class helps to do common
 * tasks through a simple interface.
 */
public class GPUImage {
    private final Context mContext;
    private final GPUImageRenderer mRenderer;
    private GLSurfaceView mGlSurfaceView;
    private GPUImageFilter mFilter;
    private Bitmap mCurrentBitmap;
    private ScaleType mScaleType = ScaleType.CENTER_CROP;

    /**
     * Instantiates a new GPUImage object.
     *
     * @param context the context
     */
    public GPUImage(@NonNull final Context context) {
        if (!supportsOpenGLES2(context)) {
            throw new IllegalStateException("OpenGL ES 2.0 is not supported on this phone.");
        }

        mContext = context;
        mFilter = new GPUImageFilter();
        mRenderer = new GPUImageRenderer(mFilter);
    }

    /**
     * Checks if OpenGL ES 2.0 is supported on the current device.
     *
     * @param context the context
     * @return true, if successful
     */
    private boolean supportsOpenGLES2(@NonNull final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    /**
     * Sets the GLSurfaceView which will display the preview.
     *
     * @param view the GLSurfaceView
     */
    public void setGLSurfaceView(@NonNull final GLSurfaceView view) {
        mGlSurfaceView = view;
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mGlSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
        mGlSurfaceView.setRenderer(mRenderer);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mGlSurfaceView.requestRender();
    }

    /**
     * Sets the background color
     *
     * @param red red color value
     * @param green green color value
     * @param blue red color value
     */
    public void setBackgroundColor(float red, float green, float blue) {
        mRenderer.setBackgroundColor(red, green, blue);
    }

    /**
     * Request the preview to be rendered again.
     */
    public void requestRender() {
        if (mGlSurfaceView != null) {
            mGlSurfaceView.requestRender();
        }
    }

    /**
     * Sets the filter which should be applied to the image which was (or will
     * be) set by setImage(...).
     *
     * @param filter the new filter
     */
    public void setFilter(@NonNull final GPUImageFilter filter) {
        mFilter = filter;
        mRenderer.setFilter(mFilter);
        requestRender();
    }

    /**
     * Sets the image on which the filter should be applied.
     *
     * @param bitmap the new image
     */
    public void setImage(@NonNull final Bitmap bitmap) {
        mCurrentBitmap = bitmap;
        mRenderer.setImageBitmap(bitmap, false);
        requestRender();
    }

    /**
     * This sets the scale type of GPUImage. This has to be run before setting the image.
     * If image is set and scale type changed, image needs to be resetBCS.
     *
     * @param scaleType The new ScaleType
     */
    public void setScaleType(@NonNull ScaleType scaleType) {
        mScaleType = scaleType;
        mRenderer.setScaleType(scaleType);
        mRenderer.deleteImage();
        mCurrentBitmap = null;
        requestRender();
    }

    public void setRotationAngle(float rotationAngle) {
        mRenderer.setRotationAngle(rotationAngle);
        requestRender();
    }

    public float getRotationAngle() {
        return mRenderer.getRotationAngle();
    }

    public void rotate(boolean clockwise) {
        mRenderer.rotate(clockwise);
        requestRender();
    }

    /**
     * Sets the rotation of the displayed image.
     *
     * @param rotation new rotation
     */
    public void setRotation(@NonNull Rotation rotation) {
        mRenderer.setRotation(rotation);
        requestRender();
    }

    public void setScaleFactor(float scaleFactor) {
        mRenderer.setScaleFactor(scaleFactor);
        requestRender();
    }

    public void setTransform(float x, float y) {
        mRenderer.setTranslate(x, y);
        requestRender();
    }

    public void setTransformOffsetLimit(float leftOffset, float rightOffset, float topOffset, float bottomOffset, float outputWidth, float outputHeight) {
        mRenderer.setTransformOffsetLimit(leftOffset, rightOffset, topOffset, bottomOffset, outputWidth, outputHeight);
    }

    @NonNull
    public PointF getCropTopLeft() {
        return mRenderer.getCropTopLeft();
    }

    @NonNull
    public PointF getCropTopRight() {
        return mRenderer.getCropTopRight();
    }

    @NonNull
    public PointF getCropBottomLeft() {
        return mRenderer.getCropBottomLeft();
    }

    @NonNull
    public PointF getCropBottomRight() {
        return mRenderer.getCropBottomRight();
    }

    public void setCropRectangle(@NonNull PointF topLeft,
                                 @NonNull PointF topRight,
                                 @NonNull PointF bottomLeft,
                                 @NonNull PointF bottomRight) {
        mRenderer.setCropRectangle(topLeft, topRight, bottomLeft, bottomRight);
    }
    /**
     * Deletes the current image.
     */
    public void deleteImage() {
        mRenderer.deleteImage();
        mCurrentBitmap = null;
        requestRender();
    }

    /**
     * Sets the image on which the filter should be applied from a Uri.
     *
     * @param uri the uri of the new image
     */
    public void setImage(@NonNull final Uri uri) {
        new LoadImageUriTask(this, uri).execute();
    }

    /**
     * Sets the image on which the filter should be applied from a File.
     *
     * @param file the file of the new image
     */
    public void setImage(@NonNull final File file) {
        new LoadImageFileTask(this, file).execute();
    }

    @Nullable
    private String getPath(@NonNull final Uri uri) {
        String[] projection = {
                MediaStore.Images.Media.DATA,
        };
        Cursor cursor = mContext.getContentResolver()
                                .query(uri, projection, null, null, null);
        int pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        String path = null;
        if (cursor.moveToFirst()) {
            path = cursor.getString(pathIndex);
        }
        cursor.close();
        return path;
    }

    /**
     * Gets the current displayed image with applied filter as a Bitmap.
     *
     * @return the current image with filter applied
     */
    @Nullable
    public Bitmap getBitmapWithFilterApplied() {
        return getBitmapWithFilterApplied(mCurrentBitmap);
    }

    /**
     * Gets the given bitmap with current filter applied as a Bitmap.
     *
     * @param bitmap the bitmap on which the current filter should be applied
     * @return the bitmap with filter applied
     */
    @Nullable
    public Bitmap getBitmapWithFilterApplied(@NonNull final Bitmap bitmap) {
        if (mGlSurfaceView != null) {
            mRenderer.deleteImage();
            mRenderer.runOnDraw(new Runnable() {

                @Override
                public void run() {
                    synchronized(mFilter) {
                        mFilter.destroy();
                        mFilter.notify();
                    }
                }
            });
            synchronized(mFilter) {
                requestRender();
                try {
                    mFilter.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        GPUImageRenderer renderer = new GPUImageRenderer(mFilter);
        renderer.setRotation(Rotation.NORMAL);
        renderer.setScaleType(mScaleType);
        PixelBuffer buffer = new PixelBuffer(bitmap.getWidth(), bitmap.getHeight());
        buffer.setRenderer(renderer);
        renderer.setImageBitmap(bitmap, false);
        Bitmap result = buffer.getBitmap();
        mFilter.destroy();
        renderer.deleteImage();
        buffer.destroy();

        mRenderer.setFilter(mFilter);
        if (mCurrentBitmap != null) {
            mRenderer.setImageBitmap(mCurrentBitmap, false);
        }
        requestRender();

        return result;
    }

    /**
     * Gets the images for multiple filters on a image. This can be used to
     * quickly get thumbnail images for filters. <br>
     * Whenever a new Bitmap is ready, the listener will be called with the
     * bitmap. The order of the calls to the listener will be the same as the
     * filter order.
     *
     * @param bitmap the bitmap on which the filters will be applied
     * @param filters the filters which will be applied on the bitmap
     * @param listener the listener on which the results will be notified
     */
    public static void getBitmapForMultipleFilters(@NonNull final Bitmap bitmap,
                                                   @NonNull final List<GPUImageFilter> filters,
                                                   @Nullable final ResponseListener<Bitmap> listener) {
        if (filters.isEmpty()) {
            return;
        }
        GPUImageRenderer renderer = new GPUImageRenderer(filters.get(0));
        renderer.setImageBitmap(bitmap, false);
        PixelBuffer buffer = new PixelBuffer(bitmap.getWidth(), bitmap.getHeight());
        buffer.setRenderer(renderer);

        for (GPUImageFilter filter : filters) {
            renderer.setFilter(filter);
            listener.response(buffer.getBitmap());
            filter.destroy();
        }
        renderer.deleteImage();
        buffer.destroy();
    }

    /**
     * Runs the given Runnable on the OpenGL thread.
     *
     * @param runnable The runnable to be run on the OpenGL thread.
     */
    void runOnGLThread(@NonNull Runnable runnable) {
        mRenderer.runOnDrawEnd(runnable);
    }

    private int getOutputWidth() {
        if (mRenderer != null && mRenderer.getFrameWidth() != 0) {
            return mRenderer.getFrameWidth();
        } else if (mCurrentBitmap != null) {
            return mCurrentBitmap.getWidth();
        } else {
            WindowManager windowManager =
                    (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            return display.getWidth();
        }
    }

    private int getOutputHeight() {
        if (mRenderer != null && mRenderer.getFrameHeight() != 0) {
            return mRenderer.getFrameHeight();
        } else if (mCurrentBitmap != null) {
            return mCurrentBitmap.getHeight();
        } else {
            WindowManager windowManager =
                    (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            return display.getHeight();
        }
    }

    private class LoadImageUriTask extends LoadImageTask {

        private final Uri mUri;

        public LoadImageUriTask(@NonNull GPUImage gpuImage, Uri uri) {
            super(gpuImage);
            mUri = uri;
        }

        @Override
        protected Bitmap decode(@NonNull BitmapFactory.Options options) {
            try {
                InputStream inputStream;
                if (mUri.getScheme().startsWith("http") || mUri.getScheme().startsWith("https")) {
                    inputStream = new URL(mUri.toString()).openStream();
                } else {
                    inputStream = mContext.getContentResolver().openInputStream(mUri);
                }
                return BitmapFactory.decodeStream(inputStream, null, options);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected int getImageOrientation() throws IOException {
            Cursor cursor = mContext.getContentResolver().query(mUri,
                                                                new String[] { MediaStore.Images.ImageColumns.ORIENTATION }, null, null, null);

            if (cursor == null || cursor.getCount() != 1) {
                return 0;
            }

            cursor.moveToFirst();
            int orientation = cursor.getInt(0);
            cursor.close();
            return orientation;
        }
    }

    private class LoadImageFileTask extends LoadImageTask {

        private final File mImageFile;

        public LoadImageFileTask(@NonNull GPUImage gpuImage, File file) {
            super(gpuImage);
            mImageFile = file;
        }

        @Override
        @Nullable
        protected Bitmap decode(@NonNull BitmapFactory.Options options) {
            return BitmapFactory.decodeFile(mImageFile.getAbsolutePath(), options);
        }

        @Override
        protected int getImageOrientation() throws IOException {
            ExifInterface exif = new ExifInterface(mImageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            switch (orientation) {
                case ExifInterface.ORIENTATION_NORMAL:
                    return 0;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        }
    }

    private abstract class LoadImageTask extends AsyncTask<Void, Void, Bitmap> {

        private final GPUImage mGPUImage;
        private int mOutputWidth;
        private int mOutputHeight;

        @SuppressWarnings("deprecation")
        public LoadImageTask(@NonNull final GPUImage gpuImage) {
            mGPUImage = gpuImage;
        }

        @Override
        @Nullable
        protected Bitmap doInBackground(@Nullable Void... params) {
            if (mRenderer != null && mRenderer.getFrameWidth() == 0) {
                try {
                    synchronized (mRenderer.mSurfaceChangedWaiter) {
                        mRenderer.mSurfaceChangedWaiter.wait(3000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mOutputWidth = getOutputWidth();
            mOutputHeight = getOutputHeight();
            return loadResizedImage();
        }

        @Override
        protected void onPostExecute(@Nullable Bitmap bitmap) {
            super.onPostExecute(bitmap);
            mGPUImage.deleteImage();
            mGPUImage.setImage(bitmap);
        }

        @Nullable
        protected abstract Bitmap decode(BitmapFactory.Options options);

        @Nullable
        private Bitmap loadResizedImage() {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            decode(options);
            int scale = 1;
            while (checkSize(options.outWidth / scale > mOutputWidth, options.outHeight / scale > mOutputHeight)) {
                scale++;
            }

            scale--;
            if (scale < 1) {
                scale = 1;
            }
            options = new BitmapFactory.Options();
            options.inSampleSize = scale;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inPurgeable = true;
            options.inTempStorage = new byte[32 * 1024];
            Bitmap bitmap = decode(options);
            if (bitmap == null) {
                return null;
            }
            bitmap = rotateImage(bitmap);
            bitmap = scaleBitmap(bitmap);
            return bitmap;
        }

        @Nullable
        private Bitmap scaleBitmap(Bitmap bitmap) {
            // resize to desired dimensions
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] newSize = getScaleSize(width, height);
            Bitmap workBitmap = Bitmap.createScaledBitmap(bitmap, newSize[0], newSize[1], true);
            if (workBitmap != bitmap) {
                bitmap.recycle();
                bitmap = workBitmap;
                System.gc();
            }

            if (mScaleType == ScaleType.CENTER_CROP) {
                // Crop it
                int diffWidth = newSize[0] - mOutputWidth;
                int diffHeight = newSize[1] - mOutputHeight;
                workBitmap = Bitmap.createBitmap(bitmap, diffWidth / 2, diffHeight / 2,
                                                 newSize[0] - diffWidth, newSize[1] - diffHeight);
                if (workBitmap != bitmap) {
                    bitmap.recycle();
                    bitmap = workBitmap;
                }
            }

            return bitmap;
        }

        /**
         * Retrieve the scaling size for the image dependent on the ScaleType.<br>
         * <br>
         * If CROP: sides are same size or bigger than output's sides<br>
         * Else   : sides are same size or smaller than output's sides
         */
        private int[] getScaleSize(int width, int height) {
            float newWidth;
            float newHeight;

            float withRatio = (float) width / mOutputWidth;
            float heightRatio = (float) height / mOutputHeight;

            boolean adjustWidth = mScaleType == ScaleType.CENTER_CROP
                    ? withRatio > heightRatio : withRatio < heightRatio;

            if (adjustWidth) {
                newHeight = mOutputHeight;
                newWidth = (newHeight / height) * width;
            } else {
                newWidth = mOutputWidth;
                newHeight = (newWidth / width) * height;
            }
            return new int[]{Math.round(newWidth), Math.round(newHeight)};
        }

        private boolean checkSize(boolean widthBigger, boolean heightBigger) {
            if (mScaleType == ScaleType.CENTER_CROP) {
                return widthBigger && heightBigger;
            } else {
                return widthBigger || heightBigger;
            }
        }

        @Nullable
        private Bitmap rotateImage(@Nullable final Bitmap bitmap) {
            if (bitmap == null) {
                return null;
            }
            Bitmap rotatedBitmap = bitmap;
            try {
                int orientation = getImageOrientation();
                if (orientation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(orientation);
                    rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                                                        bitmap.getHeight(), matrix, true);
                    bitmap.recycle();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return rotatedBitmap;
        }

        protected abstract int getImageOrientation() throws IOException;
    }

    public interface ResponseListener<T> {
        void response(T item);
    }

    public enum ScaleType { CENTER_INSIDE, CENTER_CROP }
}
