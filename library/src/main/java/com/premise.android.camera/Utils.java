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

package com.premise.android.camera;

import static android.os.Build.VERSION.SDK_INT;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewAnimationUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by kevinkawai on 3/22/17.
 */

class Utils {

    /**
     * Expand the given invisible view from the center of itself until
     * it is fully revealed.  The view will be set to View.VISIBLE.
     * If the platform is less than SDK_INT 21, then just set the
     * view visibility to View.VISIBLE without the animation.
     *
     * @param view
     */
    @TargetApi(21)
    static void expandFromCenter(View view) {

        if (SDK_INT < 21) {
            view.setVisibility(View.VISIBLE);
            return;
        }

        int cx = view.getMeasuredWidth() / 2;
        int cy = view.getMeasuredHeight() / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(view.getWidth(), view.getHeight()) / 2;

        // create the animator for this view (the start radius is zero)
        Animator anim =
                ViewAnimationUtils.createCircularReveal(view, cx, cy, 0, finalRadius);

        // make the view visible and start the animation
        view.setVisibility(View.VISIBLE);
        anim.start();
    }

    /**
     * Shrink the given visible view to the center of itself until
     * it is no longer visible.  The view will be set to View.INVISIBLE.
     * If the platform is less than SDK_INT 21, then just set the
     * view visibility to View.INVISIBLE without the animation.
     *
     * @param view
     */
    @TargetApi(21)
    static void shrinkToCenter(final View view) {

        if (SDK_INT < 21) {
            view.setVisibility(View.INVISIBLE);
            return;
        }

        // get the center for the clipping circle
        int cx = view.getMeasuredWidth() / 2;
        int cy = view.getMeasuredHeight() / 2;

        // get the initial radius for the clipping circle
        int initialRadius = view.getWidth() / 2;

        // create the animation (the final radius is zero)
        Animator anim =
                ViewAnimationUtils.createCircularReveal(view, cx, cy, initialRadius, 0);


        // start the animation
        view.setVisibility(View.INVISIBLE);
        anim.start();

    }

    /**
     * A fix for Samsung phones that have a hardware bug where all photos taken while
     * the device is in portrait orientation are saved in landscape mode to disk.
     *
     * @param bitmap
     * @param target
     * @return
     * @throws IOException
     */
    static Bitmap fixBitmapOrientation(Bitmap bitmap, File target) throws IOException {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap , 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        writeBitmapToFile(rotatedBitmap, target);
        return rotatedBitmap;
    }

    static boolean bitmapNeedsRotating(Context context, Bitmap bitmap) throws IOException {
        if (context.getResources().getConfiguration().orientation == Configuration
                .ORIENTATION_PORTRAIT) {
            return bitmap.getWidth() > bitmap.getHeight();
        }
        return false;
    }

    static void writeBitmapToFile(final Bitmap inBitmap, final File file) throws
            IOException {

        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        final FileOutputStream fos = new FileOutputStream(file);
        inBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();
    }

}
