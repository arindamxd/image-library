package com.blackbuck.mobile.image;

import android.content.Context;
import android.content.SharedPreferences;

import com.blackbuck.mobile.image.receiver.SyncReceiver;
import com.blackbuck.mobile.image.utils.Constants;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by ankit on 30/05/17.
 */

public class ImageInitializer {

    public static final int DEFAULT_CHUNK_SIZE_ON_SLOW_NETWORK = 30000; //30kB
    public static final int DEFAULT_CHUNK_SIZE_ON_FAST_NETWORK = 500000; //500kB
    public static final long DEFAULT_THROTTLING_RUN_TIME = 30 * 1000;
    public static final long DEFAULT_IMAGE_DELETE_INTERVAL = 10 * 60 * 1000;//ms
    public static final long DEFAULT_PURGE_INTERVAL = 24 * 60 * 60 * 1000;//ms
    public static final int PURGE_DURATION = 5;// days;
    public static final long DEFAULT_SYNC_DURATION = 60 * 1000;

    public static void setEndPoint(Context context, String endPoint) {
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putString(Constants.PREF_UPLOAD_ENDPOINT, endPoint).apply();
    }

    public static String getEndPoint(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getString(Constants.PREF_UPLOAD_ENDPOINT, "");
    }

    public static int getChunkSize(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getInt(Constants.PREF_CHUNK_SIZE, 30 * 1000);
    }

    public static void setChunkSize(Context context, int chunkSize) {
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putInt(Constants.PREF_CHUNK_SIZE, chunkSize).apply();
    }

    public static String getAccessToken(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getString(Constants.PREF_ACCESS_TOKEN, "");
    }

    public static void setAccessToken(Context context, String accessToken) {
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putString(Constants.PREF_ACCESS_TOKEN, accessToken).apply();
    }

    public static long getSyncDuration(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getLong(Constants.PREF_SYNC_DURATION, DEFAULT_SYNC_DURATION);
    }

    public static void setSyncDuration(Context context, long syncDuration) {
        SyncReceiver.scheduleAlarms(context);
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putLong(Constants.PREF_SYNC_DURATION, syncDuration).apply();
    }

    public static long getDeleteServiceDuration(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getLong(Constants.PREF_DELETE_SERVICE_DURATION, DEFAULT_IMAGE_DELETE_INTERVAL);
    }

    public static void setDeleteServiceDuration(Context context, long syncDuration) {
        SyncReceiver.scheduleAlarms(context);
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putLong(Constants.PREF_DELETE_SERVICE_DURATION, syncDuration).apply();
    }

    public static long getPurgeServiceDuration(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getLong(Constants.PREF_PURGE_SERVICE_DURATION, DEFAULT_PURGE_INTERVAL);
    }

    public static void setPurgeServiceDuration(Context context, long syncDuration) {
        SyncReceiver.scheduleAlarms(context);
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putLong(Constants.PREF_PURGE_SERVICE_DURATION, syncDuration).apply();
    }

    public static String getS3Bucket(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getString(Constants.PREF_S3_BUCKET, "");
    }

    public static void setS3Bucket(Context context, String s3Bucket) {
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putString(Constants.PREF_S3_BUCKET, s3Bucket).apply();
    }

    public static boolean showNotifications(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getBoolean(Constants.PREF_SHOW_NOTIFICATIONS, true);
    }

    public static void setShowNotifications(Context context, boolean showNotifications, int notificationCode) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFERENCE_IMAGE,
                Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(Constants.PREF_SHOW_NOTIFICATIONS, showNotifications).apply();
        sharedPreferences.edit().putInt(Constants.PREF_NOTIFICATION_CODE, notificationCode).apply();
    }

    public static int getNotificationCode(Context context){
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getInt(Constants.PREF_NOTIFICATION_CODE, 1000);
    }

    public static void setShowNotifications(Context context, boolean showNotifications, Class activity
            , int notificationCode) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFERENCE_IMAGE,
                Context.MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(Constants.PREF_SHOW_NOTIFICATIONS, showNotifications).apply();
        sharedPreferences.edit().putInt(Constants.PREF_NOTIFICATION_CODE, notificationCode).apply();

        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput(Constants.SERIALIZABLE_FILE_NAME, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ObjectOutputStream os = null;
        try {
            os = new ObjectOutputStream(fos);
            os.writeObject(activity);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    public static Class getNotificationClass(Context context) {
        FileInputStream fis = null;
        try {
            fis = context.openFileInput(Constants.SERIALIZABLE_FILE_NAME);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ObjectInputStream is = null;
        Class simpleClass = null;
        try {
            is = new ObjectInputStream(fis);
            simpleClass = (Class) is.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return simpleClass;
    }

    public static boolean isThrottling(Context context) {
        return context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                .getBoolean(Constants.PREF_IS_THROTTLING, false);
    }

    public static void setIsThrottling(Context context, boolean isThrottling) {
        context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                .putBoolean(Constants.PREF_IS_THROTTLING, isThrottling).apply();
    }

    public static void setEntityNamesMapping(Context context, Map<String, String> mapping) {
        if (mapping != null) {
            JSONObject object = new JSONObject(mapping);
            context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                    .putString(Constants.PREF_ENTITY_NAMES_MAPPING, object.toString()).apply();
        }
    }

    public static Map<String, String> getEntityNamesMapping(Context context) {
        Map<String, String> outputMap = new HashMap<>();
        try {
            String jsonString = context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ENTITY_NAMES_MAPPING, (new JSONObject()).toString());
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keysItr = jsonObject.keys();
            while (keysItr.hasNext()) {
                String key = keysItr.next();
                outputMap.put(key, jsonObject.get(key).toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outputMap;
    }

    public static void setImageTypesMapping(Context context, Map<String, String> mapping) {
        if (mapping != null) {
            JSONObject object = new JSONObject(mapping);
            context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE).edit()
                    .putString(Constants.PREF_IMAGE_TYPES_MAPPING, object.toString()).apply();
        }
    }

    public static Map<String, String> getImageTypesMapping(Context context) {
        Map<String, String> outputMap = new HashMap<>();
        try {
            String jsonString = context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE)
                    .getString(Constants.PREF_IMAGE_TYPES_MAPPING, (new JSONObject()).toString());
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keysItr = jsonObject.keys();
            while (keysItr.hasNext()) {
                String key = keysItr.next();
                outputMap.put(key, jsonObject.get(key).toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return outputMap;
    }
}
