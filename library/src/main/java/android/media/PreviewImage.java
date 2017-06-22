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

@RequiresApi(19)
public class PreviewImage extends Image
{
    int mFormat, mWidth, mHeight;
    Plane[] mPlanes;
    long mTimestamp;

    public PreviewImage(int imageFormat, Plane[] planes, int width, int height, long timestamp)
    {
        mFormat = imageFormat;
        mPlanes = planes;
        mWidth = width;
        mHeight = height;
        mTimestamp = timestamp;
    }

    @Override public int getFormat()
    {
        return mFormat;
    }

    @Override public int getWidth()
    {
        return mWidth;
    }

    @Override public int getHeight()
    {
        return mHeight;
    }

    @Override public long getTimestamp()
    {
        return mTimestamp;
    }

    @Override public Plane[] getPlanes()
    {
        return mPlanes;
    }

    @Override public void close()
    {
        mWidth=0;
        mHeight = 0;
        mTimestamp = 0;
        mPlanes = null;
    }
}
