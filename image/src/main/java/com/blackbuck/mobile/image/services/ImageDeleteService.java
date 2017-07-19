package com.blackbuck.mobile.image.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.blackbuck.mobile.image.ImageItem;
import com.blackbuck.mobile.image.database.ImageDataSource;
import com.blackbuck.mobile.image.receiver.SyncReceiver;
import com.blackbuck.mobile.image.utils.Constants;
import com.blackbuck.mobile.image.utils.ImageUtils;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ankit on 26/06/17.
 */

public class ImageDeleteService extends Service {

    private static final String TAG = ImageDeleteService.class.getSimpleName();
    private SharedPreferences imagePreferences;

    public ImageDeleteService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OnCreate");

        imagePreferences = getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE);
        imagePreferences.edit().putLong(Constants.PREF_DELETE_TIME, System.currentTimeMillis()).apply();
        deleteUploadedImages();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        if (intent != null) {
            SyncReceiver.completeWakefulIntent(intent);
        }
        return START_STICKY;
    }

    public void deleteUploadedImages() {
        Log.i(TAG, "DeleteUploadedImages");
        ArrayList<ImageItem> items = ImageDataSource.getUploadedImages(this);
        if (items == null || items.isEmpty()) {
            Log.e(TAG, "Nothing to delete");
            stopSelf();
            return;
        }

        for (ImageItem item : items) {
            Log.i(TAG, "deleteUploadedImages, image item = " + item);
            deleteImagesByImageType(item.imageType, item);
        }
        stopSelf();
    }

    private void deleteImagesByImageType(String imageType, ImageItem item) {

        String imageItemsPath = imagePreferences.getString(imageType, null);
        ConcurrentHashMap<String, ImageItem> imageMap;
        if (imageItemsPath != null) {
            Log.i(TAG, "pref String = " + imageItemsPath);
            imageMap = loadMapFromPreference(imageItemsPath);

            if (imageMap.containsKey(item.s3Path)) {
                Log.e(TAG, "Uploaded image s3 =" + item.s3Path + " local path =" +
                        item.imageLocalPath + " is currently in pref." + "Hence not deleting");
            } else {
                Log.i(TAG, "Image not present in " + "in pref, " +
                        "deleting from DB and actual file");
                Uri uri = Uri.parse(item.imageLocalPath);
                ImageDataSource.deleteImageByServerPath(ImageDeleteService.this, item.s3Path);
                ImageUtils.deleteFile(uri.getPath());
            }
        } else {
            Log.i(TAG, "Image not present " + "in brokerProfImageMap in pref, " +
                    "deleting from DB and actual file");
            Uri inv_uri = Uri.parse(item.imageLocalPath);
            ImageDataSource.deleteImageByServerPath(ImageDeleteService.this, item.s3Path);
            ImageUtils.deleteFile(inv_uri.getPath());
        }
    }

    public static ConcurrentHashMap<String, ImageItem> loadMapFromPreference(String jsonString) {
        Log.i(TAG, "loading Image map from preferences");
        ConcurrentHashMap<String, ImageItem> map = new ConcurrentHashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keysItr = jsonObject.keys();
            while (keysItr.hasNext()) {
                String key = keysItr.next();
                String object = jsonObject.get(key).toString();
                Gson gson = new Gson();
                ImageItem holder = gson.fromJson(object, ImageItem.class);
                map.put(key, holder);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating json object");
            e.printStackTrace();
        }
        return map;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
