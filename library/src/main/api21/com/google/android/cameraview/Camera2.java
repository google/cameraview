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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

@TargetApi(21)
class Camera2 extends CameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private final CameraManager mCameraManager;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
                    try (Image image = reader.acquireNextImage()) {
                        Image.Plane[] planes = image.getPlanes();
                        if (planes.length > 0) {
                            ByteBuffer buffer = planes[0].getBuffer();
                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);
                            mCallback.onPictureTaken(data);
                        }
                    }
                }
            };
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();
    private CameraDevice mCamera;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private String mCameraId;
    private CameraCharacteristics mCameraCharacteristics;
    private ImageReader mImageReader;
    private int mFacing;
    private AspectRatio mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;
    private final CameraDevice.StateCallback mCameraDeviceCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            mCallback.onCameraOpened();
            startCaptureSession();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            mCallback.onCameraClosed();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
            mCamera = null;
        }
    };
    private boolean mAutoFocus;
    private int mFlash;
    private int mDisplayOrientation;
    private PictureCaptureCallback mCaptureCallback = new PictureCaptureCallback() {

        @Override
        public void onPrecaptureRequired() {
            if(mCaptureSession == null) {
                Log.e(TAG, "CaptureSession is null");
                return;
            }

            if(mPreviewRequestBuilder == null) {
                Log.e(TAG, "PreviewRequestBuilder is null");
                return;
            }

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), this, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to run precapture sequence.", e);
            }
        }

        @Override
        public void onLockFocus() {
            Log.d(TAG, "Trigger AF lock start");
            if(mCaptureSession == null) {
                Log.e(TAG, "CaptureSession is null");
                return;
            }

            if(mPreviewRequestBuilder == null) {
                Log.e(TAG, "PreviewRequestBuilder is null");
                return;
            }

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to lock focus.", e);
            }
        }

        @Override
        public void onUnlockFocus() {
            Log.d(TAG, "Trigger AF lock cancel");
            if(mCaptureSession == null) {
                Log.e(TAG, "CaptureSession is null");
                return;
            }

            if(mPreviewRequestBuilder == null) {
                Log.e(TAG, "PreviewRequestBuilder is null");
                return;
            }

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to unlock focus.", e);
            }
        }

        @Override
        public void onReady() {
            captureStillPicture();
        }
    };
    private final CameraCaptureSession.StateCallback mSessionCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCamera == null) {
                return;
            }
            mCaptureSession = session;
            updateAutoFocus();
            updateFlash();
            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to start camera preview because it couldn't access camera", e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to start camera preview.", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure capture session.");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            if (mCaptureSession != null && mCaptureSession.equals(session)) {
                mCaptureSession = null;
            }
        }
    };

    Camera2(Callback callback, PreviewImpl preview, Context context) {
        super(callback, preview);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mPreview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                startCaptureSession();
            }
        });
    }

    @Override
    void start() {
        chooseCameraIdByFacing();
        collectCameraInfo();
        prepareImageReader();
        startOpeningCamera();
    }

    @Override
    void stop() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
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
        if (ratio == null || ratio.equals(mAspectRatio) ||
                !mPreviewSizes.ratios().contains(ratio)) {
            // TODO: Better error handling
            return;
        }
        mAspectRatio = ratio;
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            startCaptureSession();
        }
    }

    @Override
    boolean getAutoFocus() {
        return mAutoFocus;
    }

    @Override
    void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        mAutoFocus = autoFocus;
        if (mPreviewRequestBuilder != null) {
            updateAutoFocus();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                } catch (CameraAccessException e) {
                    mAutoFocus = !mAutoFocus; // Revert
                }
            }
        }
    }

    @Override
    int getFlash() {
        return mFlash;
    }

    @Override
    void setFlash(int flash) {
        if (mFlash == flash) {
            return;
        }
        int saved = mFlash;
        mFlash = flash;
        if (mPreviewRequestBuilder != null) {
            updateFlash();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
                } catch (CameraAccessException e) {
                    mFlash = saved; // Revert
                }
            }
        }
    }

    @Override
    void takePicture() {
        if (mAutoFocus) {
            lockFocusForCapture();
        } else {
            captureStillPicture();
        }
    }

    @Override
    void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        mPreview.setDisplayOrientation(mDisplayOrientation);
    }

    /**
     * <p>Chooses a camera ID by the specified camera facing ({@link #mFacing}).</p>
     * <p>This rewrites {@link #mCameraId}, {@link #mCameraCharacteristics}, and optionally
     * {@link #mFacing}.</p>
     */
    private void chooseCameraIdByFacing() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(mFacing);
            final String[] ids = mCameraManager.getCameraIdList();

            if (ids.length == 0) {
                mCameraId = null;
                Log.e(TAG, "No camera devices present.");
                mCallback.onCameraNotAvailable();
                return;
            }

            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    throw new NullPointerException("Unexpected state: LENS_FACING null");
                }
                if (internal == internalFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = characteristics;
                    return;
                }
            }
            // Not found
            mCameraId = ids[0];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                throw new NullPointerException("Unexpected state: LENS_FACING null");
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    mFacing = INTERNAL_FACINGS.keyAt(i);
                    return;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            mFacing = Constants.FACING_BACK;
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to get a list of camera devices", e);
        }
    }

    /**
     * <p>Collects some information from {@link #mCameraCharacteristics}.</p>
     * <p>This rewrites {@link #mPreviewSizes}, {@link #mPictureSizes}, and optionally,
     * {@link #mAspectRatio}.</p>
     */
    private void collectCameraInfo() {
        if (mCameraCharacteristics == null) {
            return;
        }

        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            throw new IllegalStateException("Failed to get configuration map: " + mCameraId);
        }
        mPreviewSizes.clear();
        for (android.util.Size size : map.getOutputSizes(mPreview.getOutputClass())) {
            mPreviewSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
        mPictureSizes.clear();
        collectPictureSizes(mPictureSizes, map);

        if (!mPreviewSizes.ratios().contains(mAspectRatio)) {
            mAspectRatio = mPreviewSizes.ratios().iterator().next();
        }
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    private void prepareImageReader() {
        Size selectedPictureSize = mCallback.onChoosePictureSize(mPictureSizes, mAspectRatio);
        Log.d(TAG, "Picture size is [" + selectedPictureSize.toString() + "]");
        mImageReader = ImageReader.newInstance(selectedPictureSize.getWidth(), selectedPictureSize.getHeight(),
                ImageFormat.JPEG, /* maxImages */ 2);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
    }

    /**
     * <p>Starts opening a camera device.</p>
     * <p>The result will be processed in {@link #mCameraDeviceCallback}.</p>
     */
    private void startOpeningCamera() {
        if (mCameraId == null) {
            return;
        }

        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to open camera: " + mCameraId, e);
        }
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mPreviewRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link #mSessionCallback}.</p>
     */
    void startCaptureSession() {
        if (!isCameraOpened() || !mPreview.isReady() || mImageReader == null) {
            return;
        }

        Size suggestedSize = Size.optimalSize(new Size(mPreview.getWidth(), mPreview.getHeight()),
                mPreviewSizes.sizes(mAspectRatio));
        Size previewSize = suggestedSize;
        if (mCallback != null) {
            previewSize = mCallback.onChoosePreviewSize(mPreviewSizes, suggestedSize, mAspectRatio);
        }
        Log.d(TAG, "Preview size is [" + previewSize.toString() + "]");
        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = mPreview.getSurface();
        try {
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionCallback, null);
        } catch (CameraAccessException e) {
            throw new RuntimeException("Failed to start camera session");
        }
    }

    /**
     * Updates the internal state of auto-focus to {@link #mAutoFocus}.
     */
    void updateAutoFocus() {
        if(mCameraCharacteristics == null) {
            Log.e(TAG, "CameraCharacteristics is null");
            return;
        }

        if(mPreviewRequestBuilder == null) {
            Log.e(TAG, "PreviewRequestBuilder is null");
            return;
        }

        if (mAutoFocus) {
            int[] modes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mAutoFocus = false;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                if (getHardwareLevelSupported() == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                } else {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                }
            }
        } else {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private int getHardwareLevelSupported() {
        if (mCameraId == null) {
            return -1;
        }
        try {
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            int deviceLevel = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            switch (deviceLevel) {
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                    Log.d(TAG, "Camera support level: INFO_SUPPORTED_HARDWARE_LEVEL_3");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                    Log.d(TAG, "Camera support level: INFO_SUPPORTED_HARDWARE_LEVEL_FULL");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    Log.d(TAG, "Camera support level: INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY");
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    Log.d(TAG, "Camera support level: INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED");
                    break;
                default:
                    Log.d(TAG, "Unknown INFO_SUPPORTED_HARDWARE_LEVEL: " + deviceLevel);
                    break;
            }
            return deviceLevel;
        } catch (Exception e) {
            Log.e(TAG, "getHardwareLevelSupported Error", e);
        }
        return -1;
    }

    /**
     * Updates the internal state of flash to {@link #mFlash}.
     */
    void updateFlash() {
        if(mPreviewRequestBuilder == null) {
            Log.e(TAG, "PreviewRequestBuilder is null");
            return;
        }

        switch (mFlash) {
            case Constants.FLASH_OFF:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_TORCH:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_RED_EYE:
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    /**
     * Locks the focus as the first step for a still image capture.
     */
    private void lockFocusForCapture() {
        if(mPreviewRequestBuilder == null) {
            Log.e(TAG, "PreviewRequestBuilder is null");
            return;
        }

        if(mCaptureCallback == null) {
            Log.e(TAG, "CaptureCallback is null");
            return;
        }

        if(mCaptureSession == null) {
            Log.e(TAG, "CaptureSession is null");
            return;
        }

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to lock focus.", e);
        }
    }

    /**
     * Captures a still picture.
     */
    void captureStillPicture() {
        if(mCamera == null) {
            Log.e(TAG, "Camera is null");
            return;
        }

        if(mPreviewRequestBuilder == null) {
            Log.e(TAG, "PreviewRequestBuilder is null");
            return;
        }

        if(mCameraCharacteristics == null) {
            Log.e(TAG, "CameraCharacteristics is null");
            return;
        }

        if(mCaptureSession == null) {
            Log.e(TAG, "CaptureSession is null");
            return;
        }

        try {
            CaptureRequest.Builder captureRequestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE));
            switch (mFlash) {
                case Constants.FLASH_OFF:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    break;
                case Constants.FLASH_ON:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                case Constants.FLASH_TORCH:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                    break;
                case Constants.FLASH_AUTO:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
                case Constants.FLASH_RED_EYE:
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    break;
            }
            // Calculate JPEG orientation.
            @SuppressWarnings("ConstantConditions") int sensorOrientation =
                    mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation +
                    mDisplayOrientation * (mFacing == Constants.FACING_FRONT ? 1 : -1) +
                    360) % 360);
            // Stop preview and capture a still picture.
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) {
                    unlockFocus(session);
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot capture a still picture.", e);
        }
    }

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    void unlockFocus(CameraCaptureSession session) {
        if(session == null) {
            Log.e(TAG, "Session is null");
            return;
        }

        if(mPreviewRequestBuilder == null) {
            Log.e(TAG, "PreviewRequestBuilder is null");
            return;
        }

        if(mCaptureCallback == null) {
            Log.e(TAG, "CaptureCallback is null");
            return;
        }

        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        try {
            session.capture(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            updateAutoFocus();
            updateFlash();
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            session.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
            mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW);
        } catch (CameraAccessException|IllegalStateException e) {
            Log.e(TAG, "Failed to restart camera preview.", e);
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} for capturing a still picture.
     */
    private static abstract class PictureCaptureCallback extends CameraCaptureSession.CaptureCallback {

        static final int STATE_PREVIEW = 0;
        static final int STATE_LOCKING = 1;
        static final int STATE_LOCKED = 2;
        static final int STATE_PRECAPTURE = 3;
        static final int STATE_WAITING = 4;
        static final int STATE_CAPTURING = 5;

        private static final long LOCK_FOCUS_DELAY_LONG = 10000;    /* 10 seconds */
        private static final long LOCK_FOCUS_DELAY_SHORT = 2000;    /* 2 seconds */

        private int mState;
        private Integer mLastAfState;
        private long focusLockTimer = Long.MAX_VALUE;
        private long focusUnlockTimer = Long.MAX_VALUE;

        PictureCaptureCallback() {
        }

        void setState(int state) {
            mState = state;
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                @NonNull TotalCaptureResult result) {
            process(result);
        }

        private void process(@NonNull CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    long now = System.currentTimeMillis();
                    if (focusLockTimer - now < 0) {
                        focusLockTimer = Long.MAX_VALUE;
                        onLockFocus();
                    } else if (focusUnlockTimer - now < 0) {
                        focusUnlockTimer = Long.MAX_VALUE;
                        onUnlockFocus();
                    }

                    // We have nothing to do when the camera preview is working normally.
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState != null && !afState.equals(mLastAfState)) {
                        switch (afState) {
                            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_INACTIVE");

                                /**
                                 * Camera is in unknown state of focus.  Schedule trying to get a focus lock now.
                                 */
                                focusLockTimer = now;                                   // Attempt to lock the focus now
                                focusUnlockTimer = Long.MAX_VALUE;                      // No unlock scheduled
                                break;
                            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN");

                                /**
                                 * The camera is currently scanning to get the best focus it can achieve.
                                 */
                                break;
                            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED");

                                /**
                                 * Attempted to focus the camera, and succeeded in getting a lock.  Schedule a long
                                 * delay before trying to focus again.
                                 */
                                focusUnlockTimer = now + LOCK_FOCUS_DELAY_LONG;
                                focusLockTimer = Long.MAX_VALUE;
                                break;
                            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED");

                                /**
                                 * Attempted to focus the camera, but did no achieve a lock.  The current focus
                                 * is probably better than not focusing at all.  Schedule a long delay to try to
                                 * refocus.
                                 */
                                focusLockTimer = now + LOCK_FOCUS_DELAY_SHORT;           // Lock focus
                                focusUnlockTimer = Long.MAX_VALUE;                      // No unlock scheduled
                                break;
                            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED");
                                focusLockTimer = Long.MAX_VALUE;                        // Nothing scheduled
                                focusUnlockTimer = Long.MAX_VALUE;
                                break;
                            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN");
                                break;
                            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                                Log.d(TAG, "CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED");
                                focusLockTimer = Long.MAX_VALUE;                        // Nothing scheduled
                                focusUnlockTimer = Long.MAX_VALUE;
                                break;
                        }
                    }
                    mLastAfState = afState;
                    break;
                }
                case STATE_LOCKING: {
                    Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (af == null) {
                        break;
                    }
                    if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                            || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING);
                            onReady();
                        } else {
                            setState(STATE_LOCKED);
                            onPrecaptureRequired();
                        }
                    }
                    break;
                }
                case STATE_PRECAPTURE: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        setState(STATE_WAITING);
                    }
                    break;
                }
                case STATE_WAITING: {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING);
                        onReady();
                    }
                    break;
                }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        public abstract void onReady();

        /**
         * Called when it is necessary to run the precapture sequence.
         */
        public abstract void onPrecaptureRequired();

        public abstract void onLockFocus();

        public abstract void onUnlockFocus();
    }
}
