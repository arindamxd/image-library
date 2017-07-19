package com.blackbuck.mobile.image;

/**
 * Created by ankit on 02/05/17.
 */

public class ImageStatus {
    public static final int ADDED = 10;
    public static final int UPLOAD_PENDING = 1;
    public static final int UPLOADING = 2;
    public static final int UPLOADED = 3;
    public static final int ERROR = 4;
    public static final int DOWNLOADED = 5;
    public static final int DOWNLOADING = 6;
    public static final int DOWNLOADABLE = 7;
    public static final int REMOVED = 8;

    public static String getStatusString(int uploadStatus) {
        switch (uploadStatus) {
            case UPLOAD_PENDING:
                return "Upload Pending";
            case UPLOADING:
                return "Uploading";
            case UPLOADED:
                return "Uploaded";
            case ERROR:
                return "Error";
            case DOWNLOADED:
                return "Downloaded";
            case DOWNLOADING:
                return "Downloading";
            case REMOVED:
                return "Marked for remove";
            default:
                throw new RuntimeException("This ImageInitializer status is not Supported. Please add this status to ImageStatus.java");
        }
    }
}
