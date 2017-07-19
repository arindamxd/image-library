package com.blackbuck.mobile.image;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ankit on 02/05/17.
 */

public class ImageItem implements Parcelable {
    public static final int DEFAULT_CHUNK_SIZE = 50 * 1024;

    public int id;
    public String entityType;
    public int entityId;
    public String identifier;
    public String imageType;
    public String s3Path;
    public String imageLocalPath;
    public long timestamp;
    public int status;
    public long bytesUploaded;
    public int noChunksCompleted;
    public int orderId;

    public ImageItem() {
    }

    public ImageItem(String eType, String ident, String iType, String sPath, String lPath, int orderId) {
        entityType = eType;
        entityId = 0;
        identifier = ident;
        imageType = iType;
        s3Path = sPath;
        imageLocalPath = lPath;
        timestamp = System.currentTimeMillis();
        this.orderId = orderId;
    }

    protected ImageItem(Parcel in) {
        id = in.readInt();
        entityType = in.readString();
        entityId = in.readInt();
        identifier = in.readString();
        imageType = in.readString();
        s3Path = in.readString();
        imageLocalPath = in.readString();
        timestamp = in.readLong();
        status = in.readInt();
        bytesUploaded = in.readLong();
        noChunksCompleted = in.readInt();
        orderId = in.readInt();
    }

    public static final Creator<ImageItem> CREATOR = new Creator<ImageItem>() {
        @Override
        public ImageItem createFromParcel(Parcel in) {
            return new ImageItem(in);
        }

        @Override
        public ImageItem[] newArray(int size) {
            return new ImageItem[size];
        }
    };

    public static String getStatusString(int uploadStatus) {
        switch (uploadStatus) {
            case ImageStatus.ADDED:
                return "Added";
            case ImageStatus.UPLOAD_PENDING:
                return "Upload Pending";
            case ImageStatus.UPLOADING:
                return "Uploading";
            case ImageStatus.UPLOADED:
                return "Uploaded";
            case ImageStatus.ERROR:
                return "Error";
            case ImageStatus.DOWNLOADED:
                return "Downloaded";
            case ImageStatus.DOWNLOADING:
                return "Downloading";
            case ImageStatus.REMOVED:
                return "Marked for removal";
        }
        return "";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(entityType);
        dest.writeInt(entityId);
        dest.writeString(identifier);
        dest.writeString(imageType);
        dest.writeString(s3Path);
        dest.writeString(imageLocalPath);
        dest.writeLong(timestamp);
        dest.writeInt(status);
        dest.writeLong(bytesUploaded);
        dest.writeInt(noChunksCompleted);
        dest.writeInt(orderId);
    }

    @Override
    public String toString() {
        return "ImageItem{" +
                "s3Path='" + s3Path + '\'' +
                ", status=" + getStatusString(status) +
                ", bytesUploaded=" + bytesUploaded +
                ", noChunksCompleted=" + noChunksCompleted +
                '}';
    }
}
