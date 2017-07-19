package com.blackbuck.mobile.image;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.res.ResourcesCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.blackbuck.mobile.image.database.ImageDataSource;
import com.blackbuck.mobile.image.tasks.DeleteImagesTask;
import com.blackbuck.mobile.image.upload.MultipartUploadService;
import com.blackbuck.mobile.image.utils.Constants;
import com.blackbuck.mobile.image.utils.ImageUtils;
import com.blackbuck.mobile.image.utils.UriUtils;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ankit on 02/05/17.
 */

public class ImageCapture {

    public static final String TAG = ImageCapture.class.getName();
    private static final String CHOOSE_IMAGE_DIALOG_TITLE = "Attach Image From";
    /* set isSingleImage to true if there is only a single image for that image type, false if multiple images are there.*/
    private boolean isSingleImage;

    /* if true, image in this group will be compressed */
    private boolean compressImages = true;

    /* set this variable true if the image has to go through CamScanner app. */
    private boolean useCamScanner;

    private String entityType;
    private int chunkSize;
    private String imageType;
    private int orderId;
    private String identifier;
    private Context context;
    private String imageTypeString = "";
    /* View Group where all images will be put */
    private LinearLayout groupView;
    private ImageEventsListener listener;
    private Object tag;
    private boolean isImageRemovable;
    private boolean selectFromGallery;
    private SharedPreferences imagePreference;

    public String getEntityType() {
        return entityType;
    }

    public String getImageType() {
        return imageType;
    }

    public String getIdentifier() {
        return identifier;
    }

    private ConcurrentHashMap<String, ImageItem> imageGroup;

    private ImageCapture(ImageBuilder builder) {
        this.isSingleImage = builder.isSingleImage;
        this.useCamScanner = builder.useCamScanner;
        this.entityType = builder.entityType;
        this.chunkSize = builder.chunkSize;
        this.imageType = builder.imageType;
        this.orderId = builder.orderId;
        this.identifier = builder.identifier;
        this.context = builder.context;
        this.compressImages = builder.compressImages;
        this.imageTypeString = builder.imageTypeString;
        this.groupView = builder.groupView;
        this.listener = builder.listener;
        this.tag = builder.tag;
        this.isImageRemovable = builder.isImageRemovable;
        this.selectFromGallery = builder.selectFromGallery;
        imageGroup = new ConcurrentHashMap<>();

        IntentFilter intentFilter = new IntentFilter(Constants.INTENT_CHUNK_UPLOAD_STATUS);
        LocalBroadcastManager.getInstance(context).registerReceiver(uploadStatusChangeReceiver, intentFilter);
        imagePreference = context.getSharedPreferences(Constants.PREFERENCE_IMAGE, Context.MODE_PRIVATE);
    }

    public class DeleteImageFiles extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... imageLocalPath) {
            if (imageLocalPath != null) {
                for (String url : imageLocalPath) {
                    try {
                        Uri uri = Uri.parse(url);
                        ImageUtils.deleteFile(uri.getPath());
                    } catch (NullPointerException ex) {
                        ex.printStackTrace();
                        Log.i(TAG, "error while deleting file " + ex.getMessage());
                    }
                }
            }
            return null;
        }
    }

    public Intent createIntentForImage() {

        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        galleryIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        if (!useCamScanner) {
            galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File imageFile = null;
        try {
            imageFile = ImageUtils.createImageFile(context);
        } catch (IOException ex) {
            Toast.makeText(context, "There was a problem clicking the image...", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error while capturing image. Unable to create new file " + ex.getMessage());
        }
        if (imageFile != null) {
            Uri fileUri = ImageUtils.getUriFromFile(context, imageFile);
            imagePreference.edit().putString(Constants.PREF_TEMPORARY_IMAGE_PATH, fileUri.getPath()).apply();
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
            if (!selectFromGallery) {
                return cameraIntent;
            }
            Intent chooserIntent = Intent.createChooser(galleryIntent, CHOOSE_IMAGE_DIALOG_TITLE);
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
            return chooserIntent;
        }
        return null;
    }

    public void setupImage(Intent data, String imagePath) {

        String compressedImageFileName = null;
        int attachCount;
        ClipData clipdata = null;
        boolean isMultipleImages = false;

        if (data == null) {
            Log.i(TAG, "Image is captured from camera");
            //For image captured from camera
            if (compressImages) {
                compressedImageFileName = ImageUtils.getCompressedImageFromTempFile(context);
            } else {
                compressedImageFileName = imagePreference.getString(Constants.PREF_TEMPORARY_IMAGE_PATH, null);
            }
            attachCount = 1;
        } else if (data.getData() != null) {
            Log.i(TAG, "Single image selection");
            if (compressImages) {
                compressedImageFileName = ImageUtils.getCompressedImageFromLocalFile(context, data.getData());
                //For single image selection
            } else {
                compressedImageFileName = ImageUtils.getUncompressedFileFromLocalFile(context,
                        UriUtils.getPath(context, data.getData()));
            }
            attachCount = 1;
        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) && (data.getClipData() != null)) {
            attachCount = data.getClipData().getItemCount();
            Log.i(TAG, "Multiple selection : " + attachCount);
            clipdata = data.getClipData();
            isMultipleImages = true;
        } else {
            Log.e(TAG, "Image Capture failed.. !! ");
            Toast.makeText(context, "Error Capturing Image. Kindly try again..", Toast.LENGTH_SHORT).show();
            return;
        }

        int i = 0;
        do {
            if (isMultipleImages) {
                if (compressImages) {
                    compressedImageFileName = ImageUtils.getCompressedImageFromLocalFile(context,
                            clipdata.getItemAt(i).getUri());
                } else {
                    compressedImageFileName = ImageUtils.getUncompressedFileFromLocalFile(context,
                            UriUtils.getPath(context, clipdata.getItemAt(i).getUri()));
                }
            }
            if (compressedImageFileName == null) {
                Log.e(TAG, "Error while saving image. Image compressedFileName was null");
                Toast.makeText(context, "Error Capturing Image. Kindly try again..", Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            File compressedFile = new File(compressedImageFileName);
            Log.i(TAG, "Image compressed File -> " + compressedFile.getAbsolutePath());
            Uri compressedContentUri = ImageUtils.getUriFromFile(context, compressedFile);

            ImageItem item = new ImageItem(entityType, String.valueOf(identifier),
                    imageType, imagePath, compressedContentUri.toString(), orderId);
            item.status = ImageStatus.ADDED;
            item.id = (int) ImageDataSource.createUploadItem(context, item);

            imageGroup.put(imagePath, item);
            i++;
        } while (i < attachCount);
        addImageToView();
    }

    public void addImageToView() {

        groupView.removeAllViews();

        View view;
        if (imageGroup.size() == 0) {
            view = LayoutInflater.from(context).inflate(R.layout.item_capture_image, groupView, false);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onCaptureImageClick(ImageCapture.this);
                }
            });
            ((TextView) view.findViewById(R.id.image_type)).setText(imageTypeString);
            groupView.addView(view);
        } else {
            for (ConcurrentHashMap.Entry<String, ImageItem> entry : imageGroup.entrySet()) {
                final ImageItem imageItem = entry.getValue();

                view = LayoutInflater.from(context).inflate(R.layout.item_captured_image, groupView, false);

                final ImageView displayImage = (ImageView) view.findViewById(R.id.captured_image);
                View removeImage = view.findViewById(R.id.remove_image);
                View addImage = view.findViewById(R.id.add_images);

                final TextView imageStatus = (TextView) view.findViewById(R.id.image_status);
                view.findViewById(R.id.upload_percent).setVisibility(View.GONE);

                displayImage.setVisibility(View.VISIBLE);
                if (imageItem.imageLocalPath != null && !imageItem.imageLocalPath.isEmpty()) {
                    Log.i(TAG, "image present in the local storage");
                    Picasso.with(context)
                            .load(imageItem.imageLocalPath)
                            .tag(TAG)
                            .centerInside()
                            .resize(dpToPx(180), dpToPx(100))
                            .into(displayImage);
                } else {
                    // If image is not present locally, download it from server using picasso and display it
                    Log.i(TAG, "Image not present locally, Downloading it from server");
                    Target target = new Target() {
                        @Override
                        public void onBitmapLoaded(final Bitmap bitmap, Picasso.LoadedFrom from) {
                            try {
                                File file = ImageUtils.createImageFile(context);
                                FileOutputStream oStream = new FileOutputStream(file);
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, oStream);
                                oStream.close();
                                imageItem.imageLocalPath = ImageUtils.getUriFromFile(context, file).toString();
                                imageItem.status = ImageStatus.DOWNLOADED;
                                ImageDataSource.updateItem(context, imageItem);
                                displayImage.setImageBitmap(bitmap);
                                setImageStatus(imageStatus, imageItem.status);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onBitmapFailed(Drawable errorDrawable) {
                        }

                        @Override
                        public void onPrepareLoad(Drawable placeHolderDrawable) {
                        }
                    };
                    displayImage.setTag(target);
                    Picasso.with(context)
                            .load(ImageInitializer.getS3Bucket(context) + imageItem.s3Path)
                            .tag(tag)
                            .centerInside()
                            .resize(dpToPx(180), dpToPx(100))
                            .into(target);
                }
                displayImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (imageItem.imageLocalPath != null) {
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.parse(imageItem.imageLocalPath), "image/*");
                            context.startActivity(intent);
                        } else {
                            Log.i(TAG, "Unable to display full size image. local image url is null");
                        }
                    }
                });

                setImageStatus(imageStatus, imageItem.status);
                if (removeImage != null) {
                    if (imageItem.status == ImageStatus.REMOVED || !isImageRemovable) {
                        removeImage.setVisibility(View.GONE);
                    } else {
                        removeImage.setVisibility(View.VISIBLE);
                    }

                    removeImage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onRemoveImage(imageItem);
                        }
                    });
                }

                if (addImage != null) {
                    addImage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (listener != null) listener.onCaptureImageClick(ImageCapture.this);
                        }
                    });

                    if (isSingleImage) {
                        addImage.setVisibility(View.GONE);
                    }
                }
                view.setTag(imageItem.s3Path);
                groupView.addView(view);
            }
        }
    }

    /***
     * Query the db images refresh the layout. This will have to be invoked only after UI has been
     * instantiated otherwise may lead to exception during refresh.
     */
    public void initializeImages(String[] imageServerUrls) {
        new FetchImagesFromDatabase(imageServerUrls).execute();
    }

    /*Fetches images from Database*/
    private class FetchImagesFromDatabase extends AsyncTask<Void, Void, Void> {
        String[] urls;

        public FetchImagesFromDatabase(String[] imageUrls) {
            urls = imageUrls;
        }

        @Override
        protected Void doInBackground(Void... param) {
            if (context != null) {
                List<ImageItem> items = ImageDataSource.getImagesByIdentifierAndType(context, identifier, imageType);
                if (!items.isEmpty()) {
                    for (ImageItem item : items) {
                        imageGroup.put(item.s3Path, item);
                    }
                }
                setImagesFromServer(urls);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (context != null) {
                imagePreference.edit().putString(imageType, new Gson().toJson(imageGroup)).apply();
            }
            if (listener != null) {
                listener.refreshImagesUI(ImageCapture.this);
            }
        }
    }

    /*
    * Takes the Documents url coming from server,
    * For multiple images iterates over s3 paths,
    * populate them to the ImageInitializer map,
    * Save those images to the preference*/
    public void setImagesFromServer(String[] imageUrls) {
        if (imageUrls != null && imageUrls.length > 0) {
            for (String imageUrl : imageUrls) {
                if (imageUrl.trim().isEmpty()) {
                    Log.e(TAG, "Image Url is empty, so Ignoring");
                    continue;
                }
                if (imageGroup.containsKey(imageUrl)) continue;

                ImageItem item = new ImageItem();
                item.entityType = entityType;
                item.identifier = identifier;
                item.imageType = imageType;
                item.s3Path = imageUrl;
                item.status = ImageStatus.DOWNLOADABLE;
                item.orderId = orderId;
                item.id = (int) ImageDataSource.createUploadItem(context, item);
                imageGroup.put(imageUrl, item);
            }
        }
    }

    private int dpToPx(int dp) {
        if (context == null) return dp;
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public void onRemoveImage(ImageItem imageItem) {
        if (imageItem != null) {
            Log.i(TAG, "removing image with s3 path " + imageItem.s3Path);
            imageGroup.remove(imageItem.s3Path, imageItem);
            if(imageItem.status == ImageStatus.ADDED || imageItem.status == ImageStatus.UPLOAD_PENDING){
                ImageDataSource.deleteImageByServerPath(context, imageItem.s3Path);
                new DeleteImagesTask().execute(imageItem.imageLocalPath);
            }else {
                imageItem.status = ImageStatus.REMOVED;
                ImageDataSource.updateItem(context, imageItem);
            }
            listener.onImageRemoved(imageItem);
            addImageToView();
        }
    }

    private void setImageStatus(TextView textView, int imageStatus) {
        textView.setText(ImageItem.getStatusString(imageStatus));
        if (context == null) return;
        switch (imageStatus) {
            case ImageStatus.ADDED:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.pending, null), null, null, null);
                break;
            case ImageStatus.UPLOAD_PENDING:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.pending, null), null, null, null);
                break;
            case ImageStatus.UPLOADING:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.uploading, null), null, null, null);
                break;
            case ImageStatus.UPLOADED:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.completed, null), null, null, null);
                break;
            case ImageStatus.ERROR:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.upload_error, null), null, null, null);
                break;
            case ImageStatus.DOWNLOADED:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.completed, null), null, null, null);
                break;
            case ImageStatus.DOWNLOADING:
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.downloading, null), null, null, null);
                break;
            case ImageStatus.REMOVED:
                textView.setTextColor(ContextCompat.getColor(context, R.color.blackbuck_red));
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.rejected_white, null), null, null, null);
                break;
        }
    }

    public ConcurrentHashMap<String, ImageItem> getImageGroup() {
        return imageGroup;
    }

    public void destroy() {
        Picasso.with(context).cancelTag(tag);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(uploadStatusChangeReceiver);
    }

    private BroadcastReceiver uploadStatusChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Upload status changed");
            if (intent == null) {
                Log.e(TAG, "intent is null, failed change status");
                return;
            }

            ImageItem item = intent.getParcelableExtra(Constants.PREF_CURRENTLY_UPLOADING_IMAGE);
            if(item != null) {
                int percentage = intent.getIntExtra(MultipartUploadService.UPLOAD_PERCENTAGE, -1);
                updateUploadProgress(item, percentage);
            }
        }
    };

    public void updateUploadProgress(ImageItem imageItem, int percent) {
        Log.i(TAG, "Updating upload progress");
        ImageItem item = imageGroup.get(imageItem.s3Path);
        if (item == null) {
            Log.i(TAG, "ImageItem is null for image map with s3 path " + imageItem.s3Path);
            return;
        }
        imageGroup.put(item.s3Path, imageItem);

        if (percent != -1) {
            View view = groupView.findViewWithTag(imageItem.s3Path);
            if (view != null) {

                TextView uploadPercent = (TextView) view.findViewById(R.id.upload_percent);
                TextView uploadStatus = (TextView) view.findViewById(R.id.image_status);

                if (item.status == ImageStatus.UPLOADING) {
                    uploadPercent.setVisibility(View.GONE);
                }
                setImageStatus(uploadStatus, item.status);
                uploadPercent.setVisibility(imageItem.status == ImageStatus.UPLOADING ? View.VISIBLE : View.GONE);
                uploadPercent.setText(percent <= 100 ? String.valueOf(percent) + "%" : "100%");
            }
        }
    }

    public void markImagesForUpload(int entityId) {
        new UpdateStatusTask(entityId).execute();
    }

    public class UpdateStatusTask extends AsyncTask<Void, Void, Void> {

        private int entityId;

        public UpdateStatusTask(int entityId) {
            this.entityId = entityId;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (context != null) {
                try {
                    for (Map.Entry<String, ImageItem> entry : imageGroup.entrySet()) {
                        ImageItem item = entry.getValue();
                        if (item.status == ImageStatus.DOWNLOADED || item.status ==
                                ImageStatus.DOWNLOADABLE || item.status == ImageStatus.REMOVED) {
                            ImageUtils.deleteFile(entry.getValue().imageLocalPath);
                            ImageDataSource.deleteImageByServerPath(context, item.s3Path);
                        }
                        if (item.status == ImageStatus.ADDED) {
                            item.status = ImageStatus.UPLOAD_PENDING;
                            item.entityId = entityId;
                            ImageDataSource.updateItem(context, item);
                        }
                    }
                } catch (Exception ex) {
                    Log.i(TAG, "exception while doing image cleanup " + ex.getMessage());
                }
            }
            return null;
        }
    }

    public boolean isEmpty() {
        int count = 0;
        ImageItem item;
        for (ConcurrentHashMap.Entry<String, ImageItem> entry : imageGroup.entrySet()) {
            item = entry.getValue();
            if (item.status != ImageStatus.REMOVED) {
                count++;
            }
        }
        return count == 0;
    }

    public int getActiveImageCount() {
        int count = 0;
        ImageItem item;
        for (ConcurrentHashMap.Entry<String, ImageItem> entry : imageGroup.entrySet()) {
            item = entry.getValue();
            if (item.status != ImageStatus.REMOVED) {
                count++;
            }
        }
        return count;
    }

    public static class ImageBuilder {
        private boolean isSingleImage;
        private boolean useCamScanner;
        private String entityType;
        private int chunkSize;
        private String imageType;
        private int orderId;
        private String identifier;
        private Context context;
        private boolean compressImages;
        private String imageTypeString;
        private LinearLayout groupView;
        private ImageEventsListener listener;
        private Object tag;
        private boolean isImageRemovable;
        private boolean selectFromGallery;

        public ImageBuilder(Context context, ImageEventsListener listener, String entityType,
                            String imageType, String identifier, LinearLayout groupView, Object tag) {
            this.context = context;
            this.listener = listener;
            this.entityType = entityType;
            this.imageType = imageType;
            this.identifier = identifier;
            this.groupView = groupView;
            this.tag = tag;
            selectFromGallery = true;
            isImageRemovable = true;
        }

        public ImageBuilder setSingleImage(boolean singleImage) {
            isSingleImage = singleImage;
            return this;
        }

        public ImageBuilder setUseCamScanner(boolean useCamScanner) {
            this.useCamScanner = useCamScanner;
            return this;
        }

        public ImageBuilder setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        public ImageBuilder setOrderId(int orderId) {
            this.orderId = orderId;
            return this;
        }

        public ImageBuilder setCompressImages(boolean compressImages) {
            this.compressImages = compressImages;
            return this;
        }

        public ImageBuilder setImageTypeString(String imageTypeString) {
            this.imageTypeString = imageTypeString;
            return this;
        }

        public ImageBuilder isImageRemovable(boolean isImageRemovable) {
            this.isImageRemovable = isImageRemovable;
            return this;
        }

        public ImageBuilder selectFromGallery(boolean selectFromGallery) {
            this.selectFromGallery = selectFromGallery;
            return this;
        }

        public ImageCapture build() {
            return new ImageCapture(this);
        }
    }
}
