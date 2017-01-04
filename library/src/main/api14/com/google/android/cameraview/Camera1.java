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

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.support.v4.util.SparseArrayCompat;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

@SuppressWarnings("deprecation")
class Camera1 extends CameraViewImpl {

    private static final int INVALID_CAMERA_ID = -1;

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();
    private Camera mCamera;
    private int mCameraId;
    private Camera.Parameters mCameraParameters;
    private AspectRatio mAspectRatio;

    private boolean mShowingPreview;

    private boolean mAutoFocus;

    private int mFacing;

    private int mFlash;

    private int mDisplayOrientation;

    Camera1(Callback callback, PreviewImpl preview) {
        super(callback, preview);
        preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                if (mCamera != null) {
                    setUpPreview();
                    adjustCameraParameters();
                }
            }
        });
    }

    @Override
    void start() {
        chooseCamera();
        openCamera();
        if(mCamera == null) {
            return;
        }

        if (mPreview.isReady()) {
            setUpPreview();
        }
        mShowingPreview = true;
        mCamera.startPreview();
    }

    @Override
    void stop() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        mShowingPreview = false;
        releaseCamera();
    }

    @SuppressLint("NewApi")
        // Suppresses Camera#setPreviewTexture
    private void setUpPreview() {
        try {
            if (mPreview.getOutputClass() == SurfaceHolder.class) {
                final boolean needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14;
                if (needsToStopPreview) {
                    mCamera.stopPreview();
                }
                mCamera.setPreviewDisplay(mPreview.getSurfaceHolder());
                if (needsToStopPreview) {
                    mCamera.startPreview();
                }
            } else {
                mCamera.setPreviewTexture((SurfaceTexture) mPreview.getSurfaceTexture());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    int getFacing() {
        return mFacing;
    }

    @Override
    void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    @Override
    Set<AspectRatio> getSupportedAspectRatios() {
        return mPreviewSizes.ratios();
    }

    @Override
    AspectRatio getAspectRatio() {
        return mAspectRatio;
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
                adjustCameraParameters();
            }
        }
    }

    @Override
    boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return mAutoFocus;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        if (setAutoFocusInternal(autoFocus)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    int getFlash() {
        return mFlash;
    }

    @Override
    void setFlash(int flash) {
        if (flash == mFlash) {
            return;
        }
        if (setFlashInternal(flash)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    void takePicture() {
        if (!isCameraOpened()) {
            throw new IllegalStateException("Camera is not ready. Call start() before takePicture().");
        }
        if (getAutoFocus()) {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    takePictureInternal();
                }
            });
        } else {
            takePictureInternal();
        }
    }

    private void takePictureInternal() {
        mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                mCallback.onPictureTaken(data);
                camera.startPreview();
            }
        });
    }

    @Override
    void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            adjustCameraParameters();
        }
    }

    /**
     * This rewrites {@link #mCameraId} and {@link #mCameraInfo}.
     */
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == mFacing) {
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

        mCamera = null;
        if(mCameraId == INVALID_CAMERA_ID) {
            mCallback.onCameraNotAvailable();
            return;
        }

        mCamera = Camera.open(mCameraId);
        mCameraParameters = mCamera.getParameters();
        // Supported preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        // Supported picture sizes;
        mPictureSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPictureSizes()) {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        // AspectRatio
        if (mAspectRatio == null) {
            mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;
        }
        adjustCameraParameters();
        mCallback.onCameraOpened();
    }

    private AspectRatio chooseAspectRatio() {
        AspectRatio r = null;
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            r = ratio;
            if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO)) {
                return ratio;
            }
        }
        return r;
    }

    private void adjustCameraParameters() {
        SortedSet<Size> sizes = mPreviewSizes.sizes(mAspectRatio);
        if (sizes == null) { // Not supported
            mAspectRatio = chooseAspectRatio();
            sizes = mPreviewSizes.sizes(mAspectRatio);
        }

        Size suggestedSize = Size.optimalSize(new Size(mPreview.getWidth(), mPreview.getHeight()), sizes);
        Size size = suggestedSize;
        if (mCallback != null) {
            size = mCallback.onChoosePreviewSize(mPreviewSizes, suggestedSize, mAspectRatio);
        }
        final Camera.Size currentSize = mCameraParameters.getPictureSize();
        if (currentSize.width != size.getWidth() || currentSize.height != size.getHeight()) {
            if (mCallback != null) {
                // Largest picture size in this ratio
                final Size pictureSize = mCallback.onChoosePictureSize(mPictureSizes, mAspectRatio);
                mCameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
            }

            mCameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        }

        int cameraRotation = calcCameraRotation(mDisplayOrientation);
        if ((mDisplayOrientation % 180) == 0) {
            mCameraParameters.set("orientation", "portrait");
        } else {
            mCameraParameters.set("orientation", "landscape");
        }

        // Special case for front facing camera1 api
        if (mFacing == CameraView.FACING_FRONT) {
            int outputRotation = cameraRotation;
            if (cameraRotation == 90) {
                outputRotation = 270;
            }

            mCameraParameters.setRotation(outputRotation);
        } else {
            mCameraParameters.setRotation(cameraRotation);
        }

        mCamera.setDisplayOrientation(cameraRotation);
        setAutoFocusInternal(mAutoFocus);
        setFlashInternal(mFlash);

        final boolean needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14;
        if (needsToStopPreview) {
            mCamera.stopPreview();
        }
        mCamera.setParameters(mCameraParameters);
        if (needsToStopPreview) {
            mCamera.startPreview();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            mCallback.onCameraClosed();
        }
    }

    private int calcCameraRotation(int rotation) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (mCameraInfo.orientation + rotation) % 360) % 360;
        } else {  // back-facing
            return (mCameraInfo.orientation - rotation + 360) % 360;
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setAutoFocusInternal(boolean autoFocus) {
        mAutoFocus = autoFocus;
        if (isCameraOpened()) {
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) && Build.VERSION.SDK_INT >= 14) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                mCameraParameters.setFocusMode(modes.get(0));
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setFlashInternal(int flash) {
        if (isCameraOpened()) {
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                return true;
            }
            String currentMode = FLASH_MODES.get(mFlash);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = Constants.FLASH_OFF;
                return true;
            }
            return false;
        } else {
            mFlash = flash;
            return false;
        }
    }
}
