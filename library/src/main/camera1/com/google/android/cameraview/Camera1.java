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

@SuppressWarnings("deprecation")
class Camera1 extends CameraViewImpl {

    private static final int INVALID_CAMERA_ID = -1;

    private final Context mContext;

    private int mCameraId;

    private Camera mCamera;

    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private final PreviewInfo mPreviewInfo = new PreviewInfo();

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

        private boolean mUpdatedCalled;

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mPreviewInfo.configure(surface, width, height);
            setUpPreview();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            mPreviewInfo.configure(surface, width, height);
            setUpPreview();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releaseCamera(); // Safe guard
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            if (!mUpdatedCalled) {
                mUpdatedCalled = true;
            }
        }
    };

    public Camera1(Context context) {
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
        setUpPreview();
        mCamera.startPreview();
    }

    private void setUpPreview() {
        try {
            mCamera.setPreviewTexture(mPreviewInfo.surface);
            mCamera.setDisplayOrientation(calcDisplayOrientation());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    void stopPreview() {
        mCamera.stopPreview();
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
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
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
