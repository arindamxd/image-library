package com.blackbuck.mobile.image;

/**
 * Created by ankit on 31/05/17.
 */

public class UploadResponse {
    public int error;
    public String message;

    @Override
    public String toString() {
        return "UploadResponse{" +
                "error=" + error +
                ", message='" + message + '\'' +
                '}';
    }
}
