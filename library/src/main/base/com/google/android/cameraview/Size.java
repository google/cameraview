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

import android.support.annotation.NonNull;
import java.util.Set;
import java.util.SortedSet;

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
public class Size implements Comparable<Size> {

    private final int mWidth;
    private final int mHeight;

    /**
     * Create a new immutable Size instance.
     *
     * @param width The width of the size, in pixels
     * @param height The height of the size, in pixels
     */
    public Size(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (o instanceof Size) {
            Size size = (Size) o;
            return mWidth == size.mWidth && mHeight == size.mHeight;
        }
        return false;
    }

    /**
     * Chooses the optimal preview size based on supplied choices
     * @param desiredSize The size to try to get closest to
     * @param choices A set of choices to select from
     * @return The picked size for camera preview.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    public static Size optimalSize(Size desiredSize, SortedSet<Size> choices) {
        int width, height;
        final int desiredWidth = desiredSize.getWidth();
        final int desiredHeight = desiredSize.getHeight();

        // Normalize to rectangle that is wider than tall
        if (desiredWidth < desiredHeight) {
            width = desiredHeight;
            height = desiredWidth;
        } else {
            width = desiredWidth;
            height = desiredHeight;
        }

        // Pick the smallest of those big enough.
        for (Size size : choices) {
            if (size.getWidth() >= width && size.getHeight() >= height) {
                return size;
            }
        }

        // If no size is big enough, pick the largest one.
        return choices.last();
    }

    @Override
    public String toString() {
        return mWidth + "x" + mHeight;
    }

    @Override
    public int hashCode() {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return mHeight ^ ((mWidth << (Integer.SIZE / 2)) | (mWidth >>> (Integer.SIZE / 2)));
    }

    @Override
    public int compareTo(@NonNull Size another) {
        return mWidth * mHeight - another.mWidth * another.mHeight;
    }
}
