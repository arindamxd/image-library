package com.blackbuck.mobile.image.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by ankit on 02/05/17.
 */

public class ImageUtils {

    private static final String TAG = ImageUtils.class.getName();

    public static void deleteFile(String filename) {
        Log.i(TAG, "deleting file with path -> " + filename);
        if (filename == null) {
            Log.e(TAG, "Error while deleting file -> file name is null");
            return;
        }
        try {
            File file = new File(filename);
            if (file.delete()) {
                Log.i(TAG, " File -> " + file.getName() + " deleted successfully");
            } else {
                Log.i(TAG, "Deletion of file " + filename + " failed.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception deleting file " + filename + " " + e.getMessage());
        }
    }

    public static String getCompressedImageFromLocalFile(Context context, Uri imageUri) {
        String compressedFileName;
        String absPath = UriUtils.getPath(context, imageUri);
        if (absPath != null) {
            File filePath = new File(absPath);
            compressedFileName = CompressionBox.compressImage(getUriFromFile(context, filePath).toString(),
                    context.getApplicationContext());
            if (compressedFileName != null) {
                File file = new File(compressedFileName);
                file.setLastModified(filePath.lastModified());
            }
        } else {
            Log.e(TAG, "Attached image Uri is null");
            Toast.makeText(context, "Error Capturing ImageInitializer. Kindly try again..", Toast.LENGTH_SHORT)
                    .show();
            return null;
        }

        if (compressedFileName == null) {
            Log.e(TAG, "compressedFileName was null, failed to compress");
            Toast.makeText(context, "Error Capturing ImageInitializer. Kindly try again..", Toast.LENGTH_SHORT)
                    .show();
            return null;
        }
        return compressedFileName;
    }

    public static String getCompressedImageFromTempFile(Context context) {
        SharedPreferences imagePref = context.getApplicationContext().
                getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE);
        String tempPhotoPath = imagePref.getString(Constants.PREF_TEMPORARY_IMAGE_PATH, null);
        if (tempPhotoPath == null) {
            Log.e(TAG, "Temporary path for image is null");
            Toast.makeText(context, "Error Capturing Image. Kindly try again..", Toast.LENGTH_SHORT)
                    .show();
            return null;
        }
        File f = new File(tempPhotoPath);
        Uri contentUri = getUriFromFile(context, f);
        String compressedFileName = CompressionBox.compressImage(contentUri.toString(),
                context.getApplicationContext());
        if (compressedFileName == null) {
            Log.e(TAG, "Failed to get compressed image path");
            Toast.makeText(context, "Error Capturing Image. Kindly try again..", Toast.LENGTH_SHORT)
                    .show();
            deleteFile(contentUri.getPath());
            return null;
        }
        deleteFile(contentUri.getPath());
        return compressedFileName;
    }

    public static String getUncompressedFileFromLocalFile(Context context, String filePath) {
        if (filePath == null) {
            Log.e(TAG, "Uncompressed File Path was null");
            Toast.makeText(context, "Error Capturing Image. Kindly try again..", Toast.LENGTH_SHORT)
                    .show();
            return null;
        }
        File f = new File(filePath);
        File newFile;
        try {
            newFile = createImageFile(context);
            createFileCopy(f, newFile);
        } catch (IOException e) {
            Log.e(TAG, "Error while copying file " + e.getMessage());
            Toast.makeText(context, "Error Capturing Image. " + e.getMessage() + ". Kindly try again.. ",
                    Toast.LENGTH_SHORT).show();
            return null;
        }

        Log.i(TAG, "getUncompressedFile copy =" + f.getAbsolutePath());
        Uri contentUri = getUriFromFile(context, newFile);
        if (contentUri == null) {
            Log.e(TAG, "contentUri was null");
            Toast.makeText(context, "Error Capturing Image. Kindly try again..", Toast.LENGTH_SHORT)
                    .show();
            return null;
        }
        return contentUri.getPath();
    }

    private static void createFileCopy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    public static File createImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    public static long getTimeStampBeforeDays(int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(System.currentTimeMillis()));
        c.add(Calendar.DATE, -1 * days);
        return c.getTime().getTime();
    }

    public static Uri getUriFromFilePath(Context context, String filePath){
        return FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName()
                        + ".provider", new File(filePath));
    }

    public static Uri getUriFromFile(Context context, File file){
        return FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName()
                + ".provider", file);
    }

}
