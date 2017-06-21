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
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.MeteringRectangle;

@TargetApi(21)
public class AutoFocusHelper {

    /** camera2 API metering region weight. */
    private static final int CAMERA2_REGION_WEIGHT = (int)
            (CameraUtil.lerp(MeteringRectangle.METERING_WEIGHT_MIN,
                    MeteringRectangle.METERING_WEIGHT_MAX,
                    Constants.METERING_REGION_FRACTION));

    /** Zero weight 3A region, to reset regions per API. */
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)
    };

    public static MeteringRectangle[] getZeroWeightRegion() {
        return ZERO_WEIGHT_3A_REGION;
    }

    /**
     * Compute 3A regions for a sensor-referenced touch coordinate.
     * Returns a MeteringRectangle[] with length 1.
     *
     * @param nx                x coordinate of the touch point, in normalized portrait
     *                          coordinates.
     * @param ny                y coordinate of the touch point, in normalized portrait
     *                          coordinates.
     * @param fraction          Fraction in [0,1]. Multiplied by min(cropRegion.width(),
     *                          cropRegion.height())
     *                          to determine the side length of the square MeteringRectangle.
     * @param cropRegion        Crop region of the image.
     * @param sensorOrientation sensor orientation as defined by
     *                          CameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION).
     */
    private static MeteringRectangle[] regionsForNormalizedCoord(float nx, float ny,
            float fraction, final Rect cropRegion, int sensorOrientation) {
        // Compute half side length in pixels.
        int minCropEdge = Math.min(cropRegion.width(), cropRegion.height());
        int halfSideLength = (int) (0.5f * fraction * minCropEdge);
        // Compute the output MeteringRectangle in sensor space.
        // nx, ny is normalized to the screen.
        // Crop region itself is specified in sensor coordinates.
        // Normalized coordinates, now rotated into sensor space.
        PointF nsc = CameraUtil.normalizedSensorCoordsForNormalizedDisplayCoords(
                nx, ny, sensorOrientation);
        int xCenterSensor = (int) (cropRegion.left + nsc.x * cropRegion.width());
        int yCenterSensor = (int) (cropRegion.top + nsc.y * cropRegion.height());
        Rect meteringRegion = new Rect(xCenterSensor - halfSideLength,
                yCenterSensor - halfSideLength,
                xCenterSensor + halfSideLength,
                yCenterSensor + halfSideLength);
        // Clamp meteringRegion to cropRegion.
        meteringRegion.left = CameraUtil.clamp(meteringRegion.left, cropRegion.left,
                cropRegion.right);
        meteringRegion.top = CameraUtil.clamp(meteringRegion.top, cropRegion.top,
                cropRegion.bottom);
        meteringRegion.right = CameraUtil.clamp(meteringRegion.right, cropRegion.left,
                cropRegion.right);
        meteringRegion.bottom = CameraUtil.clamp(meteringRegion.bottom, cropRegion.top,
                cropRegion.bottom);
        return new MeteringRectangle[]{new MeteringRectangle(meteringRegion,
                CAMERA2_REGION_WEIGHT)};
    }

    /**
     * Return AF region(s) for a sensor-referenced touch coordinate.
     *
     * <p>
     * Normalized coordinates are referenced to portrait preview window with
     * (0, 0) top left and (1, 1) bottom right. Rotation has no effect.
     * </p>
     *
     * @return AF region(s).
     */
    public static MeteringRectangle[] afRegionsForNormalizedCoord(float nx,
            float ny, final Rect cropRegion, int sensorOrientation) {
        return regionsForNormalizedCoord(nx, ny, Constants.METERING_REGION_FRACTION,
                cropRegion, sensorOrientation);
    }

    /**
     * Return AE region(s) for a sensor-referenced touch coordinate.
     *
     * <p>
     * Normalized coordinates are referenced to portrait preview window with
     * (0, 0) top left and (1, 1) bottom right. Rotation has no effect.
     * </p>
     *
     * @return AE region(s).
     */
    public static MeteringRectangle[] aeRegionsForNormalizedCoord(float nx,
            float ny, final Rect cropRegion, int sensorOrientation) {
        return regionsForNormalizedCoord(nx, ny, Constants.METERING_REGION_FRACTION,
                cropRegion, sensorOrientation);
    }


    /**
     * Calculates sensor crop region for a zoom level (zoom >= 1.0).
     *
     * @return Crop region.
     */
    public static Rect cropRegionForZoom(CameraCharacteristics characteristics, float zoom) {
        Rect sensor = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int xCenter = sensor.width() / 2;
        int yCenter = sensor.height() / 2;
        int xDelta = (int) (0.5f * sensor.width() / zoom);
        int yDelta = (int) (0.5f * sensor.height() / zoom);
        return new Rect(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta);
    }

}
