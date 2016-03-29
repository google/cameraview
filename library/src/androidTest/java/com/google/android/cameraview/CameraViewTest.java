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

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.graphics.Bitmap;
import android.os.SystemClock;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.ViewAssertion;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.TextureView;
import android.view.View;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static junit.framework.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class CameraViewTest {

    @Rule
    public final ActivityTestRule<CameraViewActivity> rule;

    public CameraViewTest() {
        rule = new ActivityTestRule<>(CameraViewActivity.class);
    }

    @Test
    public void testSetup() {
        onView(withId(R.id.camera))
                .check(matches(isDisplayed()));
        onView(withId(R.id.texture_view))
                .check(matches(isDisplayed()));
    }

    @Test
    public void preview_isShowing() {
        onView(withId(R.id.texture_view))
                .check(matches(isDisplayed()))
                // TODO: Replace below with an IdlingCallback when state listener is implemented
                .perform(new WaitAction())
                .check(new ViewAssertion() {
                    @Override
                    public void check(View view, NoMatchingViewException noViewFoundException) {
                        TextureView textureView = (TextureView) view;
                        Bitmap bitmap = textureView.getBitmap();
                        int topLeft = bitmap.getPixel(0, 0);
                        int topRight = bitmap.getPixel(0, bitmap.getHeight() - 1);
                        int bottomLeft = bitmap.getPixel(bitmap.getWidth() - 1, 0);
                        assertFalse(topLeft == topRight && topRight == bottomLeft);
                    }
                });
    }

    // A temporary workaround
    private static class WaitAction implements ViewAction {

        @Override
        public Matcher<View> getConstraints() {
            return isDisplayed();
        }

        @Override
        public String getDescription() {
            return "aaa";
        }

        @Override
        public void perform(UiController uiController, View view) {
            SystemClock.sleep(1000);
        }

    }

}
