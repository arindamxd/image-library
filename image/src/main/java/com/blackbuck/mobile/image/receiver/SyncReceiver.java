package com.blackbuck.mobile.image.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import com.blackbuck.mobile.image.ImageInitializer;
import com.blackbuck.mobile.image.database.ImageDataSource;
import com.blackbuck.mobile.image.services.ImageDeleteService;
import com.blackbuck.mobile.image.services.PurgeService;
import com.blackbuck.mobile.image.upload.MultipartUploadService;
import com.blackbuck.mobile.image.utils.Connectivity;
import com.blackbuck.mobile.image.utils.Constants;

/**
 * Created by ankit on 30/05/17.
 */

public class SyncReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = SyncReceiver.class.getName();

    public static void scheduleAlarms(Context context) {
        Log.i(TAG, "registering alarm");
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, SyncReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);

        long syncDuration = ImageInitializer.getSyncDuration(context);
        mgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() +
                syncDuration, syncDuration, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive, intent =" + intent);
        launchSyncTasks(context);
    }

    private static void launchSyncTasks(Context context) {

        if (Connectivity.isConnected(context)) {
            if (ImageDataSource.getPendingImageCount(context) > 0) {
                Log.d(TAG, "Starting MultipartUploadService");
                Intent imageUpload = new Intent(context, MultipartUploadService.class);
                startWakefulService(context, imageUpload);
            } else {
                Log.i(TAG, "NOT Starting MultipartUploadService, no images to upload");
            }
        } else {
            Log.i(TAG, "NOT Starting MultipartUploadService. NO CONNECTIVITY");
        }

        SharedPreferences imagePreferences = context.getSharedPreferences(Constants.PREFERENCE_IMAGE,
                Context.MODE_PRIVATE);

        if (imagePreferences.contains(Constants.PREF_DELETE_TIME)) {
            long deleteTimeStamp = imagePreferences.getLong(Constants.PREF_DELETE_TIME, 0);
            if (System.currentTimeMillis() - deleteTimeStamp > ImageInitializer.getDeleteServiceDuration(context)) {
                Log.i(TAG, "Starting Image Delete Service since elpased time" +
                        "is greater than 10 mins");
                Intent imageDelete = new Intent(context, ImageDeleteService.class);
                startWakefulService(context, imageDelete);
            } else {
                Log.i(TAG, "NOT Starting Image Delete Service since elapsed time" +
                        "is not greater than 10 mins");
            }
        } else {
            Log.i(TAG, "Starting Image Delete Service, since pref doesn't contain" +
                    "deleteTimestamp");
            Intent imageDelete = new Intent(context, ImageDeleteService.class);
            startWakefulService(context, imageDelete);
        }


        if (imagePreferences.contains(Constants.PREF_PURGE_TIME)) {
            long purgeTimeStamp = imagePreferences.getLong(Constants.PREF_PURGE_TIME, 0);
            if (System.currentTimeMillis() - purgeTimeStamp > ImageInitializer.getPurgeServiceDuration(context)) {
                Log.i(TAG, "Starting Purge Service since elpased time" +
                        "is greater than 24 hours");
                Intent purgeIntent = new Intent(context, PurgeService.class);
                startWakefulService(context, purgeIntent);
            } else {
                Log.i(TAG, "NOT Starting Purge Service since elpased time" +
                        "is not greater than 24 hours");
            }
        } else {
            Log.i(TAG, "Starting Purge Service, since pref doesnt contain" +
                    "purgetimestamp");
            Intent purgeIntent = new Intent(context, PurgeService.class);
            startWakefulService(context, purgeIntent);
        }


    }
}
