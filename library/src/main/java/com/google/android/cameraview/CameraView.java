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
import android.util.AttributeSet;
import android.view.TextureView;
import android.widget.FrameLayout;

public class CameraView extends FrameLayout {

    private final CameraViewImpl mImpl;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (Build.VERSION.SDK_INT < 21) {
            mImpl = new Camera1(context);
        } else {
            mImpl = new Camera1(context); // TODO: Implement Camera2 and replace this
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

}
