# Thanks for choosing image-library !

Follow this guide to setup Image Module for your application. This module will take care of capturing images from camera, displaying them, saving them to database, uploading them to server, downloading images, caching them and displaying.

## SETUP

### Step 1 -> Add the following permissions to your AndroidManifest.xml file
```
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.CAMERA
android.permission.WAKE_LOCK
```
### Step 2 -> Add the following dependency to your build.gradle file -
```
compile 'com.blackbuck.library:image:1.0'
```

## USAGE

### STEP 1 -> 
Instantiate For each image type, you have to create an instance of ImageCapture class. You have to invoke ImageBuilder class constructor public ImageBuilder(Context context, ImageEventsListener listener, String entityType, String imageType, String identifier, LinearLayout groupView, Object tag){}

**ImageEventsListener** - Your fragment/activity class should implement this interface to listen to the events related to image.

**groupView** - Is a linear layout in your view where images of a particular image type will be displayed.

You can also call additional optional methods such as -

**setCompressImages(Context context, boolean compress)** Passing compress true will automatically compress the images captured.

**setImageTypeString(Context context, String name)** This is image Name used for visual information purpose only.

**setSingleImage(Context context, boolean single)** you can control whether only a single image can be clicked for a single image type or multiple images through this method

**setShowNotifications(Context context, boolean showNotification** passing true will automatically start showing progress of image upload in notifications.

**setIsThrottling(Context context, boolean isThrottling)** Pass true if your some other network requests are going on in your app. This will enable the throttling check while uploading image and the upload service will stop. Pass false once the network is free.

**setEntityNamesMapping(Context context, Map<String, String> mapping)** Pass a hashmap which maps entity types with their names(a readable name for that entity). This is used in showing upload notifications.

**setImageTypesMapping(Context context, Map<String, String> mapping)** Pass a hashmap which maps image types with their names(a readable name for that image type). This is used in showing upload notifications.

An example of Instantiation is -

```
ImageCapture imageCapture = new ImageCapture.ImageBuilder(getActivity(), this, "ENTITY_1",
            "IMAGE_TYPE_1", "identifier", linearlayout, TAG)
            .setCompressImages(true)
            .setSingleImage(true)
            .build();
```

### STEP 2 -> 
Override ImageEventsListener interface methods
```
@Override
public void onCaptureImageClick(ImageCapture imageCapture) {
    // This method is called whenever a user clicks on addImage button
    Intent intent = imageCapture.createIntentForImage();
    startActivityForResult(intent, REQUEST_CODE);
}

@Override
public void refreshImagesUI(ImageCapture imageCapture) {
    // This method can be invoked by the library to refresh the image list. To avoid unexpected crashes, check if fragment or activity is visible before 
    if (isVisible() && !isRemoving()) {
        imageCapture.addImageToView();
    }
}
```
### STEP 3 -> 
Implement onActivityResult() method
```
@Override
public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK) {
        if (requestCode == REQUEST_CODE) {
            imageCapture.setupImage(data, "path_of_image_on_server");
        } 
    }
}
```

### STEP 4 -> 
Override onStop() for destroying ImageCapture object
```
@Override
public void onStop() {
    imageCapture.destroy();
    super.onStop();
}
```

### STEP 5 -> 
Before using this library you have to send some config parameters to the library. You can do this initialization in your application class or your launcher activity.
```
ImageInitializer.setAccessToken(this, "access_token"); // access_token to make api call
ImageInitializer.setEndPoint(this, "server_endpoint"); // full path where image will be uploaded
ImageInitializer.setChunkSize(this, 30 * 1024);    // size of individual chunk for uploading. If you do not provide this value, library will send chunks of 30kb in case of 2G network and 500kb chunks in case of 3G/4g/wifi
ImageInitializer.setSyncDuration(this, 60 * 100); // time interval after which upload service should retry uploading in case the upload service is stopped.
ImageInitializer.setS3Bucket(this, BuildConfig.S3_BUCKET); // s3 bucket where the images are stored.
```
***Using custom view***

If you don't want to use the default layout used for image items, you can supply your own. Create a xml layout file with name item_captured_image.xml. In this xml file you have to define views

```
captured_image(ImageView where the image will be displayed), 
image_status(TextView where current status of the image will be displayed), 
upload_percent(TextView where the upload percent will be displayed), 
remove_image(View which will be used to remove a particular image), 
add_images(View which will enable users to add more images of the same type).
```

For more details, checkout the [WIKI](https://github.com/BLACKBUCK-LABS/image-library/wiki) page
