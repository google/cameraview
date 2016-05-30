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


import android.hardware.Camera;
import android.support.v4.util.SparseArrayCompat;

@SuppressWarnings("deprecation")
class Camera1Constants {

    private static SparseArrayCompat<String> sFocusModesMap;

    static String convertFocusMode(int focusMode) {
        if (sFocusModesMap == null) {
            sFocusModesMap = new SparseArrayCompat<>();
            sFocusModesMap.put(Constants.FOCUS_MODE_OFF, Camera.Parameters.FOCUS_MODE_FIXED);
            sFocusModesMap.put(Constants.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_AUTO);
            sFocusModesMap.put(Constants.FOCUS_MODE_MACRO, Camera.Parameters.FOCUS_MODE_MACRO);
            sFocusModesMap.put(Constants.FOCUS_MODE_CONTINUOUS_PICTURE,
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            sFocusModesMap.put(Constants.FOCUS_MODE_CONTINUOUS_VIDEO,
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            sFocusModesMap.put(Constants.FOCUS_MODE_EDOF, Camera.Parameters.FOCUS_MODE_EDOF);
        }
        return sFocusModesMap.get(focusMode);
    }

}
