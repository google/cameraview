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

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.hardware.display.DisplayManagerCompat;
import android.support.v4.view.ViewCompat;
import android.view.Display;
import android.view.View;


/**
 * Some more addition to {@link android.support.v4.view.ViewCompat}.
 */
class ViewCompat2 {

    private ViewCompat2() {
    }

    /**
     * Gets the logical display to which the view's window has been attached.
     *
     * @param view The view.
     * @return The logical display, or null if the view is not currently attached to a window.
     */
    public static Display getDisplay(@NonNull View view) {
        if (Build.VERSION.SDK_INT >= 17) {
            return view.getDisplay();
        }
        return ViewCompat.isAttachedToWindow(view) ?
                DisplayManagerCompat.getInstance(view.getContext())
                        .getDisplay(Display.DEFAULT_DISPLAY) : null;
    }

}
