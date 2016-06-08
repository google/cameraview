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

import android.graphics.SurfaceTexture;


/**
 * Stores information about the {@link SurfaceTexture} showing camera preview.
 */
class SurfaceInfo {

    SurfaceTexture surface;
    int width;
    int height;

    void configure(SurfaceTexture s, int w, int h) {
        surface = s;
        width = w;
        height = h;
    }

}
