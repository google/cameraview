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

import android.graphics.Matrix;
import android.graphics.RectF;

/**
 * Transform coordinates to and from preview coordinate space and camera driver
 * coordinate space.
 */
public class CameraCoordinateTransformer {

    // http://developer.android.com/guide/topics/media/camera.html#metering-focus-areas
    private static final RectF CAMERA_DRIVER_RECT = new RectF(-1000, -1000, 1000, 1000);
    private final Matrix mCameraToPreviewTransform;
    private final Matrix mPreviewToCameraTransform;

    /**
     * Convert rectangles to / from camera coordinate and preview coordinate space.
     *
     * @param mirrorX            if the preview is mirrored along the X axis.
     * @param displayOrientation orientation in degrees.
     * @param previewRect        the preview rectangle size and position.
     */
    public CameraCoordinateTransformer(boolean mirrorX, int displayOrientation,
            RectF previewRect) {
        if (!hasNonZeroArea(previewRect)) {
            throw new IllegalArgumentException("previewRect");
        }
        mCameraToPreviewTransform = cameraToPreviewTransform(mirrorX, displayOrientation,
                previewRect);
        mPreviewToCameraTransform = inverse(mCameraToPreviewTransform);
    }

    /**
     * Transform a rectangle in camera space into a new rectangle in preview
     * view space.
     *
     * @param source the rectangle in camera space
     * @return the rectangle in preview view space.
     */
    public RectF toPreviewSpace(RectF source) {
        RectF result = new RectF();
        mCameraToPreviewTransform.mapRect(result, source);
        return result;
    }

    /**
     * Transform a rectangle in preview view space into a new rectangle in
     * camera view space.
     *
     * @param source the rectangle in preview view space
     * @return the rectangle in camera view space.
     */
    public RectF toCameraSpace(RectF source) {
        RectF result = new RectF();
        mPreviewToCameraTransform.mapRect(result, source);
        return result;
    }

    private Matrix cameraToPreviewTransform(boolean mirrorX, int displayOrientation,
            RectF previewRect) {
        Matrix transform = new Matrix();
        // Need mirror for front camera.
        transform.setScale(mirrorX ? -1 : 1, 1);
        // Apply a rotate transform.
        // This is the value for android.hardware.Camera.setDisplayOrientation.
        transform.postRotate(displayOrientation);
        // Map camera driver coordinates to preview rect coordinates
        Matrix fill = new Matrix();
        fill.setRectToRect(CAMERA_DRIVER_RECT,
                previewRect,
                Matrix.ScaleToFit.FILL);
        // Concat the previous transform on top of the fill behavior.
        transform.setConcat(fill, transform);
        return transform;
    }

    private Matrix inverse(Matrix source) {
        Matrix newMatrix = new Matrix();
        source.invert(newMatrix);
        return newMatrix;
    }

    private boolean hasNonZeroArea(RectF rect) {
        return rect.width() != 0 && rect.height() != 0;
    }
}