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

package android.media;

import android.support.annotation.RequiresApi;

import java.nio.ByteBuffer;

/**
 * Represents a Plane in a PreviewImage. The buffer in a PreviewPlane represents an entire image,
 * not just a single plane.
 */
@RequiresApi(19)
public class PreviewPlane extends Image.Plane {

    int mRowStride;
    int mPixelStride;
    ByteBuffer mBuffer;

    public PreviewPlane(int rowStride, int pixelStride, byte[] buffer)
    {
        mRowStride = rowStride;
        mPixelStride = pixelStride;
        mBuffer = ByteBuffer.wrap(buffer);
    }

    /** @return always returns 0.  PreviewImages have multiple planes in one buffer, so row stride
     * cannot be reliable represented with one value.  You should use the ImageFormat to tell you
     * how the bytes are arranged in the buffer.  **/
    @Override public int getRowStride() {
        return 0;
    }

    /** @return always returns 0.  PreviewImages have multiple planes in one buffer, so pixel stride
     * cannot be reliable represented with one value.  You should use the ImageFormat to tell you
     * how the bytes are arranged in the buffer.  **/
    @Override public int getPixelStride() {
        return 0;
    }

    @Override public ByteBuffer getBuffer() {
        return mBuffer;
    }

    /** clears the pixel buffer**/
    public void clear()
    {
        if(mBuffer != null) {
            mBuffer.clear();
        }
    }
}
