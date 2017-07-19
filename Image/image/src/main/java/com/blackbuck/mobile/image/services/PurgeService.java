package com.blackbuck.mobile.image.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.blackbuck.mobile.image.ImageInitializer;
import com.blackbuck.mobile.image.ImageItem;
import com.blackbuck.mobile.image.database.ImageDataSource;
import com.blackbuck.mobile.image.tasks.DeleteImagesTask;
import com.blackbuck.mobile.image.utils.Constants;
import com.blackbuck.mobile.image.utils.ImageUtils;

import java.util.List;

/**
 * Created by ankit on 27/06/17.
 */

public class PurgeService extends Service {

    private static final String TAG = PurgeService.class.getName();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "OnCreate");
        Log.i(TAG, "Putting long =" + System.currentTimeMillis() + " into pref");
        SharedPreferences imagePreference = getSharedPreferences(Constants.PREFERENCE_IMAGE,
                Context.MODE_PRIVATE);
        imagePreference.edit().putLong(Constants.PREF_PURGE_TIME, System.currentTimeMillis()).apply();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        startPurging();
        return START_STICKY;
    }

    private void startPurging() {
        Log.i(TAG, "startPurging");
        purgeImageDB();
        stopSelf();
    }

    private void purgeImageDB() {
        Log.i(TAG, "purgeImageDB");
        long timeStamp = ImageUtils.getTimeStampBeforeDays(ImageInitializer.PURGE_DURATION);
        List<ImageItem> imageItems = ImageDataSource.getPurgeableImages(this, timeStamp);
        String[] imagePaths = new String[imageItems.size()];
        int i = 0;
        for (ImageItem item : imageItems) {
            imagePaths[i] = item.imageLocalPath;
        }
        new DeleteImagesTask().execute(imagePaths);
        int deletedRows = ImageDataSource.purgeImages(this, timeStamp);
        Log.i(TAG, "purgeImageDB, deleted no of rows: " + deletedRows);
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
