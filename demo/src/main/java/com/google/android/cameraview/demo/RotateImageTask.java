package com.google.android.cameraview.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;

import java.io.ByteArrayOutputStream;

/**
 * Created by damian on 09/11/2016.
 *
 * Compressing and rotating byte array
 */
class RotateImageTask extends AsyncTask<byte[], Void, byte[]> {

    private CompressImageCallback mListener;
    private int angle;

    public RotateImageTask(CompressImageCallback mListener, int angle) {
        this.mListener = mListener;
        this.angle = angle;
    }

    @Override
    protected byte[] doInBackground(byte[]... params) {
        byte[] data = params[0];
        Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);
        Bitmap bmp = rotateBitmap(image, angle);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    @Override
    protected void onPostExecute(byte[] bitmap) {
        super.onPostExecute(bitmap);
        mListener.onImageCompressed(bitmap);
    }

    private Bitmap rotateBitmap(Bitmap source, int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

}
