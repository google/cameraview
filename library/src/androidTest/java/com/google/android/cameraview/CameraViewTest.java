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

import org.junit.Test;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.rule.UiThreadTestRule;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CameraViewTest {

    public final UiThreadTestRule rule;

    public CameraViewTest() {
        rule = new UiThreadTestRule();
    }

    @Test
    @UiThreadTest
    public void testConstructor() {
        Context context = InstrumentationRegistry.getTargetContext();
        CameraView cameraView = new CameraView(context);
        assertThat(cameraView.getChildCount(), is(0));
    }

}
