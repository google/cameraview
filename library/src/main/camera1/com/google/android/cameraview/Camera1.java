/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Set;
import java.util.SortedSet;

@SuppressWarnings("deprecation")
class Camera1 extends CameraViewImpl {

    private static final int INVALID_CAMERA_ID = -1;

    private static final AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(4, 3);

    private final Context mContext;

    private int mCameraId;

    private Camera mCamera;

    private Camera.Parameters mCameraParameters;

    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private final PreviewInfo mPreviewInfo = new PreviewInfo();

    private final SizeMap mPreviewSizes = new SizeMap();

    private AspectRatio mAspectRatio;

    private int mDisplayOrientation;

    private static class PreviewInfo {
        SurfaceTexture surface;
        int width;
        int height;

        void configure(SurfaceTexture s, int w, int h) {
            surface = s;
            width = w;
            height = h;
        }
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mPreviewInfo.configure(surface, width, height);
            if (mCamera != null) {
                setUpPreview();
                adjustPreviewSize();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            mPreviewInfo.configure(surface, width, height);
            if (mCamera != null) {
                setUpPreview();
                adjustPreviewSize();
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releaseCamera(); // Safe guard
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    public Camera1(Context context, Callback callback) {
        super(callback);
        mContext = context;
    }

    @Override
    TextureView.SurfaceTextureListener getSurfaceTextureListener() {
        return mSurfaceTextureListener;
    }

    @Override
    void onResume() {
        chooseCamera();
        openCamera();
    }

    @Override
    void onPause() {
        releaseCamera();
    }

    @Override
    void startPreview() {
        if (mPreviewInfo.surface != null) {
            setUpPreview();
        }
        mCamera.startPreview();
    }

    private void setUpPreview() {
        try {
            mCamera.setPreviewTexture(mPreviewInfo.surface);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    void stopPreview() {
        mCamera.stopPreview();
    }

    @Override
    SizeMap getSupportedPreviewSizes() {
        return mPreviewSizes;
    }

    @Override
    boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    void setAspectRatio(AspectRatio ratio) {
        if (mAspectRatio == null || !isCameraOpened()) {
            // Handle this later when camera is opened
            mAspectRatio = ratio;
        } else if (!mAspectRatio.equals(ratio)) {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null) {
                throw new UnsupportedOperationException(ratio + " is not supported");
            } else {
                mAspectRatio = ratio;
                adjustPreviewSize();
            }
        }
    }

    @Override
    AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    int getDisplayOrientation() {
        return mDisplayOrientation;
    }

    /**
     * This rewrites {@link #mCameraId} and {@link #mCameraInfo}.
     */
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            // TODO: Just choose the back-facing camera for now
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    private void openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        mCamera = Camera.open(mCameraId);
        mCameraParameters = mCamera.getParameters();
        // Supported preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        // AspectRatio
        if (mAspectRatio == null) {
            mAspectRatio = DEFAULT_ASPECT_RATIO;
        }
        adjustPreviewSize();
        // Display orientation
        mDisplayOrientation = calcDisplayOrientation();
        mCamera.setDisplayOrientation(mDisplayOrientation);
        mCallback.onCameraOpened();
    }

    private AspectRatio chooseAspectRatio() {
        AspectRatio r = null;
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            r = ratio;
            if (ratio.equals(DEFAULT_ASPECT_RATIO)) {
                return ratio;
            }
        }
        return r;
    }

    private void adjustPreviewSize() {
        final SortedSet<Size> sizes = mPreviewSizes.sizes(mAspectRatio);
        if (sizes == null) { // Not supported
            mAspectRatio = chooseAspectRatio();
        }
        Size size = chooseOptimalSize(sizes);
        mCameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        mCamera.setParameters(mCameraParameters);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size chooseOptimalSize(SortedSet<Size> sizes) {
        if (mPreviewInfo.width == 0 || mPreviewInfo.height == 0) { // Not yet laid out
            return sizes.first(); // Return the smallest size
        }
        int desiredWidth;
        int desiredHeight;
        if (mDisplayOrientation == 90 || mDisplayOrientation == 270) {
            desiredWidth = mPreviewInfo.height;
            desiredHeight = mPreviewInfo.width;
        } else {
            desiredWidth = mPreviewInfo.width;
            desiredHeight = mPreviewInfo.height;
        }
        Size result = null;
        for (Size size : sizes) { // Iterate from small to large
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight()) {
                return size;

            }
            result = size;
        }
        return result;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            mCallback.onCameraClosed();
        }
    }

    private int calcDisplayOrientation() {
        WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            int result = (mCameraInfo.orientation + degrees) % 360;
            return (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            return (mCameraInfo.orientation - degrees + 360) % 360;
        }
    }

}
