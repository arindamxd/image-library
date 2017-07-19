package com.blackbuck.mobile.image.upload;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.blackbuck.mobile.image.ImageInitializer;
import com.blackbuck.mobile.image.ImageItem;
import com.blackbuck.mobile.image.ImageStatus;
import com.blackbuck.mobile.image.R;
import com.blackbuck.mobile.image.UploadResponse;
import com.blackbuck.mobile.image.database.ImageDataSource;
import com.blackbuck.mobile.image.receiver.SyncReceiver;
import com.blackbuck.mobile.image.utils.Connectivity;
import com.blackbuck.mobile.image.utils.Constants;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/***
 * Created by dell on 29/1/16.
 */
public class MultipartUploadService extends Service {
    public static final String TAG = MultipartUploadService.class.getName();
    public static final String PREF_UPLOADING = "PREF_UPLOADING";
    public static final String IS_UPLOADING = "IS_UPLOADING";
    public static final String UPLOAD_PERCENTAGE = "UPLOAD_PERCENTAGE";

    public static final String UPLOAD_ERROR_THROTTLING = "Throttling";
    public static final String UPLOAD_ERROR_REQUEST_TIMEOUT = "Request Timeout";

    private Handler imageUploadHandler;
    private Handler throttlingHandler;
    private LocalBroadcastManager localBroadcastManager;
    private Call<UploadResponse> uploadCall;

    private ImageItem imageItem;
    private File uploadFile;
    private int noPendingImages;
    private Map<String, String> entityTypeMapping;
    private Map<String, String> imageTypeMapping;

    @Override
    public void onCreate() {
        super.onCreate();
        throttlingHandler = new Handler();
        imageUploadHandler = new Handler();
        localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        entityTypeMapping = ImageInitializer.getEntityNamesMapping(this);
        imageTypeMapping = ImageInitializer.getImageTypesMapping(this);
        uploadImageToServer();
        Log.d(TAG, "starting multipart upload service");
    }

    private void uploadImageToServer() {
        noPendingImages = ImageDataSource.getPendingImageCount(this);
        if (noPendingImages == 0) {
            Log.d(TAG, "No images to upload, stopping service");
            stopSelf();
            return;
        } else {
            imageItem = ImageDataSource.getImageToUpload(this);
            if (imageItem == null) {
                Log.d(TAG, "No upload images, stopping the service");
                stopSelf();
                return;
            }
        }
        if (getFileS3Path() == null || !getFileS3Path().equals(imageItem.s3Path)) {
            setFileS3Path(imageItem.s3Path);
            setStartTime(System.currentTimeMillis());
        }

        if (!Connectivity.isConnected(this)) {
            Log.d(TAG, "No internet connection, Stopping the service");
            stopSelf();
            return;
        }

        if (ImageInitializer.isThrottling(this)) {
            Log.d(TAG, "Network getting throttled, Stopping the service");
            stopSelf();
            return;
        }

        imageItem.status = ImageStatus.UPLOADING;

        ImageDataSource.updateItem(this, imageItem);
        sendChunkUploadNotification();
        updateNotificationProgress(ImageItem.getStatusString(ImageStatus.UPLOADING));
        uploadChunksToServer();
    }

    private void uploadChunksToServer() {

        Log.d(TAG, "uploadChunksToServer ");

        if (!Connectivity.isConnected(this)) {
            Log.d(TAG, "No internet connection, Stopping the service");
            ImageDataSource.updateItem(this, imageItem);
            stopSelf();
            return;
        }

        if (ImageInitializer.isThrottling(this)) {
            Log.d(TAG, "Network getting throttled, Stopping the service");
            imageItem.status = ImageStatus.ERROR;
            ImageDataSource.updateItem(this, imageItem);
            sendChunkUploadNotification();
            sendFullImageUploadBroadcast(UPLOAD_ERROR_THROTTLING, null);
            updateNotificationProgress(getString(R.string.notification_status_paused));
            stopSelf();
            return;
        }

        Uri uri = Uri.parse(imageItem.imageLocalPath);
        uploadFile = new File(uri.getPath());
        if (uploadFile.exists() && uploadFile.length() != 0) {
            BufferedInputStream bis = null;
            FileOutputStream out = null;

            final int chunkSize = getChunkSize();
            byte[] buffer = new byte[chunkSize];
            try {
                bis = new BufferedInputStream(new FileInputStream(uploadFile));
                bis.skip(imageItem.bytesUploaded);
                final int chunk;
                chunk = bis.read(buffer);
                File partFile = new File(uploadFile.getParent(), "file_chunk.jpg");
                if (partFile.exists()) {
                    partFile.delete();
                }

                out = new FileOutputStream(partFile);
                try {
                    out.write(buffer, 0, chunk);
                } catch (ArrayIndexOutOfBoundsException ex) {
                    ex.printStackTrace();
                }

                RequestBody fbody = RequestBody.create(MediaType.parse("image"), partFile);
                UploadServices service = RetrofitInitializerHelper.getUploadServiceHelper(this);

                final boolean isFinalChunk = !(bis.available() > 0);

                Log.d(TAG, "Uploading chunk for " + imageItem);

                uploadCall = service.uploadChunkToServer(ImageInitializer.getEndPoint(this),
                        "Token :" + ImageInitializer.getAccessToken(this), fbody,
                        ImageInitializer.getAccessToken(this), isFinalChunk ? 1 : 0, imageItem.s3Path,
                        imageItem.noChunksCompleted + 1, imageItem.entityType, imageItem.entityId,
                        imageItem.imageType);
                uploadCall.enqueue(new Callback<UploadResponse>() {

                    @Override
                    public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                        if (response.body() != null && response.code() == 200) {
                            UploadResponse uploadResponse = response.body();
                            if (uploadResponse.error == 0) {
                                Log.d(TAG, "Chunk uploaded successfully " + imageItem);

                                throttlingHandler.removeCallbacks(throttlingRunnable);
                                throttlingHandler.postDelayed(throttlingRunnable,
                                        ImageInitializer.DEFAULT_THROTTLING_RUN_TIME);

                                imageUploadHandler.removeCallbacks(uploadRunnable);
                                imageUploadHandler.postDelayed(uploadRunnable,
                                        ImageInitializer.DEFAULT_THROTTLING_RUN_TIME);

                                imageItem.bytesUploaded += chunkSize;
                                imageItem.noChunksCompleted++;
                                if (isFinalChunk) {
                                    Log.d(TAG, "Image uploaded successfully " + imageItem);
                                    noPendingImages--;
                                    imageItem.status = ImageStatus.UPLOADED;
                                    ImageDataSource.updateItem(MultipartUploadService.this, imageItem);

                                    sendFullImageUploadBroadcast(null, String.valueOf(response.code()));
                                    sendChunkUploadNotification();

                                    if (noPendingImages < 1) {
                                        Log.d(TAG, "No other images to upload, Stopping the service");
                                        completeUploadNotification();
                                        stopSelf();
                                    } else {
                                        updateNotificationProgress(getString(R.string.notification_status_success));
                                        Log.d(TAG, "More images to upload, uploading the next image");
                                        uploadImageToServer();
                                    }
                                } else {
                                    uploadChunksToServer();
                                    sendChunkUploadNotification();
                                    updateNotificationProgress(getString(R.string.notification_uploading));
                                }
                            } else {
                                Log.e(TAG, "Server error in uploading chunk " + uploadResponse.message);
                                onError(uploadResponse.message, String.valueOf(response.code()));
                            }

                        } else {
                            Log.e(TAG, "Server error in uploading image " + response.errorBody().toString());
                            onError(response.errorBody().toString(), String.valueOf(response.code()));
                        }
                    }

                    @Override
                    public void onFailure(Call<UploadResponse> call, Throwable t) {
                        Log.e(TAG, "Server error in uploading image " + t.getMessage());
                        onError(t.getMessage(), null);
                    }
                });
            } catch (FileNotFoundException e) {
                ImageDataSource.deleteImageByServerPath(this, imageItem.s3Path);
                imageItem = ImageDataSource.getImageToUpload(this);
                noPendingImages--;
                if (imageItem == null) {
                    throttlingHandler.removeCallbacks(throttlingRunnable);
                    imageUploadHandler.removeCallbacks(uploadRunnable);
                    stopSelf();
                } else {
                    uploadChunksToServer();
                }
            } catch (IOException e) {
                Log.e(TAG, "IO Exception");
                e.printStackTrace();
                stopSelf();
            } finally {
                try {
                    if (bis != null) {
                        bis.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.e(TAG, "requested file doesn't exist on storage, uploading next file");
            ImageDataSource.deleteImageByServerPath(this, imageItem.s3Path);
            imageItem = ImageDataSource.getImageToUpload(this);
            noPendingImages--;
            if (imageItem == null) {
                throttlingHandler.removeCallbacks(throttlingRunnable);
                imageUploadHandler.removeCallbacks(uploadRunnable);
                stopSelf();
            } else {
                uploadChunksToServer();
            }
        }
    }

    private void updateNotificationProgress(String status) {
        try {
            RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.image_notification);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setAutoCancel(true)
                    .setContent(remoteViews);

            if(getIntentForNotification() != null){
                builder.setContentIntent(getIntentForNotification());
            }

            if (uploadFile == null) {
                uploadFile = new File(Uri.parse(imageItem.imageLocalPath).getPath());
            }

            try {
                int currInPerc = (int) ((imageItem.bytesUploaded * 100) / uploadFile.length());
                remoteViews.setProgressBar(R.id.upload_progress, 100, (currInPerc > 100 ? 100 : currInPerc), false);
                remoteViews.setTextViewText(R.id.upload_percent, String.valueOf(currInPerc > 100 ? 100 : currInPerc) + "%");
            } catch (ArithmeticException ex) {
                ex.printStackTrace();
            }

            remoteViews.setTextViewText(R.id.title, getString(R.string.notification_title, noPendingImages));
            remoteViews.setTextViewText(R.id.entity_type, entityTypeMapping.get(imageItem.entityType) + " - "
                    + imageItem.identifier);
            remoteViews.setTextViewText(R.id.image_type, imageTypeMapping.get(imageItem.imageType));
            remoteViews.setTextViewText(R.id.upload_status, status);

            Notification notification = builder.build();
            notification.bigContentView = remoteViews;

            NotificationManager notificationmanager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationmanager.notify(ImageInitializer.getNotificationCode(this), notification);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void completeUploadNotification() {
        try {
            RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.image_notification);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_notification_small)
                    .setAutoCancel(true)
                    .setContent(remoteViews);

            if(getIntentForNotification() != null){
                builder.setContentIntent(getIntentForNotification());
            }

            remoteViews.setProgressBar(R.id.upload_progress, 100, 100, false);
            remoteViews.setTextViewText(R.id.upload_percent, "100%");

            remoteViews.setTextViewText(R.id.title, getString(R.string.images_uploaded_successfully));
            remoteViews.setViewVisibility(R.id.entity_type, View.GONE);
            remoteViews.setViewVisibility(R.id.image_type, View.GONE);
            remoteViews.setViewVisibility(R.id.upload_status, View.GONE);

            Notification notification = builder.build();
            notification.bigContentView = remoteViews;

            NotificationManager notificationmanager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationmanager.notify(ImageInitializer.getNotificationCode(this), notification);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private PendingIntent getIntentForNotification(){
        PendingIntent pendingIntent = null;
        Class notificationClass = ImageInitializer.getNotificationClass(this);
        if(notificationClass != null){
            Intent intent = new Intent(this, notificationClass);
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return pendingIntent;
    }

    private void onError(String message, String code) {
        imageItem.status = ImageStatus.ERROR;
        ImageDataSource.updateItem(MultipartUploadService.this, imageItem);
        sendChunkUploadNotification();
        updateNotificationProgress(getString(R.string.notification_status_error));
        sendFullImageUploadBroadcast(message, code);
        imageUploadHandler.removeCallbacks(uploadRunnable);
        throttlingHandler.removeCallbacks(throttlingRunnable);
        stopSelf();
    }

    private Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (uploadCall != null) {
                    uploadCall.cancel();
                }
                Log.d(TAG, "Request timeout in uploading image");
                imageItem.status = ImageStatus.UPLOAD_PENDING;
                ImageDataSource.updateItem(MultipartUploadService.this, imageItem);

                sendChunkUploadNotification();
                sendFullImageUploadBroadcast(UPLOAD_ERROR_REQUEST_TIMEOUT, null);
                updateNotificationProgress(getString(R.string.notification_status_paused));
                stopSelf();
            } catch (Exception e) {
                updateNotificationProgress(getString(R.string.notification_status_error));
                stopSelf();
            }
        }
    };

    private Runnable throttlingRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                try {
                    Log.d(TAG, "Stopping the service, request throttling");
                    if (uploadCall != null) {
                        uploadCall.cancel();
                    }
                    imageItem.status = ImageStatus.UPLOAD_PENDING;
                    ImageDataSource.updateItem(MultipartUploadService.this, imageItem);
                    sendFullImageUploadBroadcast(UPLOAD_ERROR_THROTTLING, null);
                    sendChunkUploadNotification();
                    updateNotificationProgress(getString(R.string.notification_status_paused));
                    stopSelf();
                    return;
                } catch (Exception e) {
                    updateNotificationProgress(getString(R.string.notification_status_error));
                    stopSelf();
                    return;
                }
            } catch (Exception e) {
                updateNotificationProgress(getString(R.string.notification_status_error));
                stopSelf();
            }
        }
    };

    private void sendFullImageUploadBroadcast(String errorMessage, String errorCode) {
        if (uploadFile == null && imageItem == null) {
            return;
        }

        Intent uploadComplete = new Intent(Constants.INTENT_IMAGE_UPLOAD_STATUS);
        uploadComplete.putExtra(Constants.IMAGE_ITEM, imageItem);
        uploadComplete.putExtra(Constants.UPLOAD_TIME, System.currentTimeMillis() - getStartTime());
        uploadComplete.putExtra(Constants.NO_OF_PENDING_IMAGES, noPendingImages);
        if (errorMessage != null) {
            uploadComplete.putExtra(Constants.ERROR_MESSAGE, errorMessage);
        }
        if (errorCode != null) {
            uploadComplete.putExtra(Constants.ERROR_CODE, errorCode);
        }
        localBroadcastManager.sendBroadcast(uploadComplete);
    }

    private void sendChunkUploadNotification() {
        try {
            if (uploadFile == null && imageItem == null) {
                return;
            }
            if (uploadFile == null) {
                uploadFile = new File(Uri.parse(imageItem.imageLocalPath).getPath());
            }
            int percentage = imageItem.status == ImageStatus.UPLOADED ? 100 :
                    (int) Math.ceil(imageItem.bytesUploaded * 100 / uploadFile.length());
            Intent uploadChange = new Intent(Constants.INTENT_CHUNK_UPLOAD_STATUS);
            uploadChange.putExtra(UPLOAD_PERCENTAGE, percentage);
            uploadChange.putExtra(Constants.IMAGE_ITEM, imageItem);
            localBroadcastManager.sendBroadcast(uploadChange);
        } catch (ArithmeticException ex) {
            ex.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "MultipartUploadService onStartCommand");
        if (intent != null) {
            SyncReceiver.completeWakefulIntent(intent);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (uploadCall != null) {
                uploadCall.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (imageUploadHandler != null && uploadRunnable != null) {
            imageUploadHandler.removeCallbacks(uploadRunnable);
        }
        if (throttlingHandler != null && throttlingRunnable != null) {
            throttlingHandler.removeCallbacks(throttlingRunnable);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private final static String PREF_IMAGE_UPLOAD_TIMING = "IMAGE_UPLOAD_TIMING";
    private final static String START_TIME = "START_TIME";
    private final static String IMAGE_S3_PATH = "S3_PATH";

    public void setStartTime(long startTime) {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(PREF_IMAGE_UPLOAD_TIMING, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(START_TIME, startTime);
        editor.apply();
    }

    public Long getStartTime() {
        return getApplicationContext().getSharedPreferences(PREF_IMAGE_UPLOAD_TIMING, Context.MODE_PRIVATE).getLong(START_TIME, 0);
    }

    public void setFileS3Path(String s3Path) {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(PREF_IMAGE_UPLOAD_TIMING, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(IMAGE_S3_PATH, s3Path);
        editor.apply();
    }

    public String getFileS3Path() {
        return getApplicationContext().getSharedPreferences(PREF_IMAGE_UPLOAD_TIMING, Context.MODE_PRIVATE).getString(IMAGE_S3_PATH, null);
    }

    private int getChunkSize() {
        int size = ImageInitializer.getChunkSize(this);
        if (size == 0) {
            return Connectivity.isConnectedFast(this) ? ImageInitializer.DEFAULT_CHUNK_SIZE_ON_FAST_NETWORK :
                    ImageInitializer.DEFAULT_CHUNK_SIZE_ON_SLOW_NETWORK;
        }
        return size;
    }
}
