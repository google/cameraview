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

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.TextureView;
import android.widget.FrameLayout;

import java.util.ArrayList;

public class CameraView extends FrameLayout {

    private final CameraViewImpl mImpl;

    private final InternalCallbacks mCallbacks;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mCallbacks = new InternalCallbacks();
        if (Build.VERSION.SDK_INT < 21) {
            mImpl = new Camera1(context, mCallbacks);
        } else {
            mImpl = new Camera1(context, mCallbacks); // TODO: Implement Camera2 and replace this
        }
        inflate(context, R.layout.camera_view, this);
        TextureView textureView = (TextureView) findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(mImpl.getSurfaceTextureListener());
    }

    /**
     * This needs to be called from {@link Activity#onResume()}.
     */
    public void onResume() {
        mImpl.onResume();
    }

    /**
     * This needs to be called from {@link Activity#onPause()}.
     */
    public void onPause() {
        mImpl.onPause();
    }

    public void startPreview() {
        mImpl.startPreview();
    }

    public void stopPreview() {
        mImpl.stopPreview();
    }

    public SizeMap getSupportedPreviewSizes() {
        return mImpl.getSupportedPreviewSizes();
    }

    public boolean isCameraOpened() {
        return mImpl.isCameraOpened();
    }

    public void addCallback(@NonNull Callback callback) {
        mCallbacks.add(callback);
    }

    public void removeCallback(@NonNull Callback callback) {
        mCallbacks.remove(callback);
    }

    private class InternalCallbacks implements InternalCameraViewCallback {

        private final ArrayList<Callback> mCallbacks = new ArrayList<>();

        public void add(Callback callback) {
            mCallbacks.add(callback);
        }

        public void remove(Callback callback) {
            mCallbacks.remove(callback);
        }

        @Override
        public void onCameraOpened() {
            for (Callback callback : mCallbacks) {
                callback.onCameraOpened(CameraView.this);
            }
        }

        @Override
        public void onCameraClosed() {
            for (Callback callback : mCallbacks) {
                callback.onCameraClosed(CameraView.this);
            }
        }
    }

    @SuppressWarnings("UnusedParameters")
    public abstract static class Callback {

        public void onCameraOpened(CameraView cameraView) {
        }

        public void onCameraClosed(CameraView cameraView) {
        }
    }

}
