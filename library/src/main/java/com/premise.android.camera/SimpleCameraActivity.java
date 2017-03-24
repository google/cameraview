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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;
import com.google.android.cameraview.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.CountDownLatch;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
public class SimpleCameraActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        AspectRatioFragment.Listener {

    private static final String TAG = "MainActivity";

    private static final boolean DO_ANIM = true;

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_OFF,
            CameraView.FLASH_AUTO,
            CameraView.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_off,
            R.string.flash_auto,
            R.string.flash_on,
    };

    private int mCurrentFlash;

    private CameraView mCameraView;

    private ImageView mCapturedImageView;

    private Handler mBackgroundHandler;

    private FloatingActionButton mTakePicButton;

    private CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private MediaActionSound mSound;

    private Bitmap mBitmap;

    private Uri mOutputUri;

    private boolean mIsDestroyed;

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.take_picture) {
                if (mBitmap != null) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    takePhoto();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_camera_layout);
        mCameraView = (CameraView) findViewById(R.id.camera);
        mCameraView.setFlash(CameraView.FLASH_OFF);
        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }
        mTakePicButton = (FloatingActionButton) findViewById(R.id.take_picture);
        mTakePicButton.setOnClickListener(mOnClickListener);
        mCapturedImageView = (ImageView)findViewById(R.id.captured_camera_image);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
        initSound();
        if (getIntent() != null && getIntent().hasExtra(MediaStore.EXTRA_OUTPUT)) {
            mOutputUri = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        } else {
            throw new IllegalArgumentException("SimpleCameraActivity must be passed MediaStore.EXTRA_OUTPUT");
        }
    }

    private void initSound() {
        if (SDK_INT >= 16) {
            mSound = new MediaActionSound();
        }
    }

    private void playSound() {
        if (SDK_INT >= 16) {
            if (mSound != null) {
                mSound.play(MediaActionSound.SHUTTER_CLICK);
            }
        }
    }

    private void releaseSound() {
        if (SDK_INT >= 16) {
            if (mSound != null) {
                mSound.release();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mCameraView.start();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ConfirmationDialogFragment
                    .newInstance(R.string.camera_permission_confirmation,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA_PERMISSION,
                            R.string.camera_permission_not_granted)
                    .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mIsDestroyed = true;
        if (mBackgroundHandler != null) {
            if (SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mCapturedImageView.setImageDrawable(null);
            mBitmap.recycle();
        }
        releaseSound();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show();
                }
                // No need to start camera here; it is handled by onResume
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.simple_camera_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        //cannot use resource ID in switch statements in android library projects
        if (item.getItemId() == R.id.aspect_ratio) {
            if (mCameraView != null) {
                final Set<AspectRatio> ratios = mCameraView.getSupportedAspectRatios();
                final AspectRatio currentRatio = mCameraView.getAspectRatio();
                AspectRatioFragment.newInstance(ratios, currentRatio)
                        .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
            }
        } else if (item.getItemId() == R.id.switch_flash) {
            if (mCameraView != null) {
                mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
                item.setTitle(FLASH_TITLES[mCurrentFlash]);
                item.setIcon(FLASH_ICONS[mCurrentFlash]);
                mCameraView.setFlash(FLASH_OPTIONS[mCurrentFlash]);
            }
        } else if (item.getItemId() == R.id.switch_camera) {
            if (mCameraView != null) {
                int facing = mCameraView.getFacing();
                mCameraView.setFacing(facing == CameraView.FACING_FRONT ?
                        CameraView.FACING_BACK : CameraView.FACING_FRONT);
            }
        }
        return false;
    }

    @Override
    public void onAspectRatioSelected(@NonNull AspectRatio ratio) {
        if (mCameraView != null) {
            Toast.makeText(this, ratio.toString(), Toast.LENGTH_SHORT).show();
            mCameraView.setAspectRatio(ratio);
        }
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened");
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            Log.d(TAG, "onPictureTaken " + data.length);
            //Toast.makeText(cameraView.getContext(), R.string.picture_taken, Toast.LENGTH_SHORT)
            //       .show();
            getBackgroundHandler().post(new Runnable() {
                @Override
                public void run() {
                    File file = new File(mOutputUri.getPath());
                    OutputStream os = null;
                    try {
                        if (isActivityDestroyed()) { //check: android image processing takes time
                            return;
                        }
                        os = new FileOutputStream(file);
                        os.write(data);  //
                        os.close();
                        if (isActivityDestroyed()) { //check: writing to disk takes time
                            return;
                        }
                        mBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                        if (Utils.bitmapNeedsRotating(SimpleCameraActivity.this,mBitmap)) {
                            mBitmap = Utils.fixBitmapOrientation(mBitmap, file);
                        }
                        if (isActivityDestroyed()) { //check: bitmap processing takes time
                            return;
                        }
                        mCountDownLatch.await();
                        mCountDownLatch = new CountDownLatch(1);
                        if (isActivityDestroyed()) { //check: animation can take time
                            return;
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                revealCapturedImage(mBitmap);
                            }
                        });
                    } catch (IOException e) {
                        Log.w(TAG, "Cannot write to " + file, e);
                        Toast.makeText(SimpleCameraActivity.this,
                                R.string.camera_failed_to_capture_image,
                                Toast.LENGTH_SHORT).show();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while writing image to disk", e);
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
            });
        }

    };

    public static class ConfirmationDialogFragment extends DialogFragment {

        private static final String ARG_MESSAGE = "message";
        private static final String ARG_PERMISSIONS = "permissions";
        private static final String ARG_REQUEST_CODE = "request_code";
        private static final String ARG_NOT_GRANTED_MESSAGE = "not_granted_message";

        public static ConfirmationDialogFragment newInstance(@StringRes int message,
                String[] permissions, int requestCode, @StringRes int notGrantedMessage) {
            ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, message);
            args.putStringArray(ARG_PERMISSIONS, permissions);
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = args.getStringArray(ARG_PERMISSIONS);
                                    if (permissions == null) {
                                        throw new IllegalArgumentException();
                                    }
                                    ActivityCompat.requestPermissions(getActivity(),
                                            permissions, args.getInt(ARG_REQUEST_CODE));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getActivity(),
                                            args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .create();
        }

    }


    private void setCameraButtonImage(final int imageRes) {
        mTakePicButton.setImageResource(imageRes);
        mTakePicButton.setEnabled(true);
        Utils.expandFromCenter(mTakePicButton);
    }

    @TargetApi(21)
    private void revealCapturedImage(Bitmap bitmap) {

        Log.d("MainActivity", "revealCapturedImage");

        setCameraButtonImage(R.drawable.ic_check_white_24dp);
        if (SDK_INT < 21 || !DO_ANIM) {
            mCapturedImageView.setVisibility(View.VISIBLE);
            mCapturedImageView.setImageBitmap(bitmap);
            return;
        }

        int cx = mCapturedImageView.getMeasuredWidth() / 2;
        int cy = mCapturedImageView.getMeasuredHeight() / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(mCapturedImageView.getWidth(), mCapturedImageView.getHeight()) / 2;

        // create the animator for this view (the start radius is zero)
        Animator anim =
                ViewAnimationUtils.createCircularReveal(mCapturedImageView, cx, cy, 0, finalRadius);

        // make the view visible and start the animation
        anim.start();
        mCapturedImageView.setVisibility(View.VISIBLE);
        mCapturedImageView.setImageBitmap(bitmap);
    }



    @TargetApi(21)
    private void takePhoto() {

        playSound();
        mTakePicButton.setEnabled(false);
        Utils.shrinkToCenter(mTakePicButton);
        lockScreenOrientation();
        mCameraView.takePicture();

        if (SDK_INT < 21 || !DO_ANIM) {
            mCameraView.setVisibility(View.INVISIBLE);
            mCountDownLatch.countDown();
            return;
        }

        // previously visible view

        // get the center for the clipping circle
        int cx = mCameraView.getMeasuredWidth() / 2;
        int cy = mCameraView.getMeasuredHeight() / 2;

        // get the initial radius for the clipping circle
        int initialRadius = mCameraView.getWidth() / 2;

        // create the animation (the final radius is zero)
        Animator anim =
                ViewAnimationUtils.createCircularReveal(mCameraView, cx, cy, initialRadius, 0);

        // make the view invisible when the animation is done
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mCameraView.setVisibility(View.INVISIBLE);
                mCountDownLatch.countDown();
            }
        });

        // start the animation
        anim.start();
    }

    private void lockScreenOrientation() {
        if (getResources().getConfiguration().orientation == Configuration
                .ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); //lock orientation
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    /**
     * Embedded in this layout, just finish activity
     * @param view
     */
    void finish(View view) {
        finish();
    }


    private boolean isActivityDestroyed() {
        if (SDK_INT >= 17) {
            return isDestroyed();
        }
        return isFinishing() || mIsDestroyed;
    }

}