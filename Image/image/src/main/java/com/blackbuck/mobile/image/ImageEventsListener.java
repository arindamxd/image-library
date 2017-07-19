package com.blackbuck.mobile.image;

/**
 * Created by ankit on 02/05/17.
 */

public interface ImageEventsListener {
    void onCaptureImageClick(ImageCapture handler);
    void refreshImagesUI(ImageCapture handler);
    void onImageRemoved(ImageItem imageItem);
}
