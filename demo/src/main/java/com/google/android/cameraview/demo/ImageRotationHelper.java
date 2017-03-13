package com.google.android.cameraview.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.google.android.cameraview.CameraView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by rajesh on 26/1/17.
 */

public class ImageRotationHelper {
    // isOldCameraApi is a flag I use if the Preview is Camera1 or Camera2 because on older devices
    // you just have to rotate the image on certain types
    public static Bitmap correctImageOrientation(File file, int cameraType) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = null;
        try {
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeStream(new FileInputStream(file), null, options);
            ExifInterface ei = new ExifInterface(file.getAbsolutePath());
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            return getRotatedBitmap(bitmap, orientation, cameraType);
        } catch (IOException e) {
            e.printStackTrace();
            return bitmap;
        }
    }

    private static Bitmap getRotatedBitmap(Bitmap bitmap, int orientation, int cameraType) {


        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                bitmap = rotateImage(bitmap, 90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                bitmap = rotateImage(bitmap, 180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                bitmap = rotateImage(bitmap, 270);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
                if (cameraType == CameraView.FACING_FRONT) {
                    bitmap = rotateImage(bitmap, 180);
                }
                break;
            default:
                break;
        }
        return bitmap;
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
