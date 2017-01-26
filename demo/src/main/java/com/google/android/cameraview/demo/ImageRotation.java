package com.google.android.cameraview.demo;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.io.File;

/**
 * Created by rajesh on 26/1/17.
 */

class ImageRotation extends AsyncTask<Void, Void, Bitmap> {
    private String path;
    private MainActivity.ImageRotator imageRotator;
    private String TAG = "ImageRotation";
    private int cameraType;

    ImageRotation(String path, MainActivity.ImageRotator imageRotator, int cameraType) {
        this.path = path;
        this.imageRotator = imageRotator;
        this.cameraType = cameraType;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        return ImageRotationHelper.correctImageOrientation(new File(path), cameraType);
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        super.onPostExecute(bitmap);
        imageRotator.rotateImage(bitmap);
    }
}
