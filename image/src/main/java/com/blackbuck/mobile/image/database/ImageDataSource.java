package com.blackbuck.mobile.image.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.blackbuck.mobile.image.ImageItem;
import com.blackbuck.mobile.image.ImageStatus;
import com.blackbuck.mobile.image.upload.MultipartUploadService;

import java.util.ArrayList;
import java.util.List;

/***
 * Created by Ankit Aggarwal on 11/7/16.
 */
public class ImageDataSource {

    private static final String TAG = ImageDataSource.class.getName();

    public synchronized static long createUploadItem(Context context, ImageItem item) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(ImageSQLiteHelper.COL_IMAGE_LOCAL_PATH, item.imageLocalPath);
        values.put(ImageSQLiteHelper.COL_BYTES_UPLOADED, item.bytesUploaded);
        values.put(ImageSQLiteHelper.COL_NO_COMPLETED_CHUNKS, item.noChunksCompleted);
        values.put(ImageSQLiteHelper.COL_ENTITY_TYPE, item.entityType);
        values.put(ImageSQLiteHelper.COL_ENTITY_ID, item.entityId);
        values.put(ImageSQLiteHelper.COL_IMAGE_TYPE, item.imageType);
        values.put(ImageSQLiteHelper.COL_UPLOAD_STATUS, item.status);
        values.put(ImageSQLiteHelper.COL_S3_PATH, item.s3Path);
        values.put(ImageSQLiteHelper.COL_IDENTIFIER, item.identifier);
        values.put(ImageSQLiteHelper.COL_TIMESTAMP, item.timestamp);
        values.put(ImageSQLiteHelper.COL_ORDER_ID, item.orderId);

        long item_id = db.insertWithOnConflict(ImageSQLiteHelper.TABLE_IMAGE_ITEM, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        item.id = (int) item_id;
        Log.i(TAG, "upload item inserted with id " + item_id);
        db.close();
        return item_id;
    }

    public synchronized static void updateItem(Context context, ImageItem item) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(ImageSQLiteHelper.COL_IMAGE_LOCAL_PATH, item.imageLocalPath);
        values.put(ImageSQLiteHelper.COL_BYTES_UPLOADED, item.bytesUploaded);
        values.put(ImageSQLiteHelper.COL_NO_COMPLETED_CHUNKS, item.noChunksCompleted);
        values.put(ImageSQLiteHelper.COL_ENTITY_TYPE, item.entityType);
        values.put(ImageSQLiteHelper.COL_ENTITY_ID, item.entityId);
        values.put(ImageSQLiteHelper.COL_IMAGE_TYPE, item.imageType);
        values.put(ImageSQLiteHelper.COL_UPLOAD_STATUS, item.status);
        values.put(ImageSQLiteHelper.COL_IDENTIFIER, item.identifier);
        values.put(ImageSQLiteHelper.COL_ORDER_ID, item.orderId);
        int count = db.update(ImageSQLiteHelper.TABLE_IMAGE_ITEM, values,
                ImageSQLiteHelper.COL_S3_PATH + " = '" + item.s3Path + "'", null);
        if (count > 0) {
            Log.i(TAG, "updated " + count + " items successfully");
        } else {
            Log.e(TAG, "no item updated");
        }
        db.close();
    }

    public static synchronized ArrayList<ImageItem> getImagesByIdentifierAndType(Context context, String ident, String type) {
        ArrayList<ImageItem> imageItemsList;
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getReadableDatabase();
        Cursor cursor = db.query(ImageSQLiteHelper.TABLE_IMAGE_ITEM, null, ImageSQLiteHelper.COL_IDENTIFIER + " =? AND " +
                        ImageSQLiteHelper.COL_IMAGE_TYPE + " =?", new String[]{ident, type}, null, null,
                ImageSQLiteHelper.COL_TIMESTAMP + " ASC", null);
        imageItemsList = getItemsList(cursor);
        cursor.close();
        db.close();
        return imageItemsList;
    }

    public synchronized static ImageItem getImageToUpload(Context context) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getWritableDatabase();
        List<ImageItem> itemsList;
        String selectQuery = "SELECT * FROM " + ImageSQLiteHelper.TABLE_IMAGE_ITEM + " WHERE " +
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " = " + ImageStatus.UPLOAD_PENDING + " OR " +
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " = " + ImageStatus.ERROR + " OR " +
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " = " + ImageStatus.UPLOADING + " LIMIT 1";
        Cursor cursor = db.rawQuery(selectQuery, null);
        itemsList = getItemsList(cursor);
        cursor.close();
        db.close();
        return itemsList.size() > 0 ? itemsList.get(0) : null;
    }

    public synchronized static List<ImageItem> getAllImages(Context context) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getWritableDatabase();
        List<ImageItem> itemsList;
        String selectQuery = "SELECT * FROM " + ImageSQLiteHelper.TABLE_IMAGE_ITEM;
        Cursor cursor = db.rawQuery(selectQuery, null);
        itemsList = getItemsList(cursor);
        cursor.close();
        db.close();
        return itemsList;
    }

    public static synchronized ArrayList<ImageItem> getUploadedImages(Context context) {
        ArrayList<ImageItem> imageItemsList;
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getReadableDatabase();
        Cursor cursor = db.query(ImageSQLiteHelper.TABLE_IMAGE_ITEM, null,
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " =?",
                new String[]{String.valueOf(ImageStatus.UPLOADED)}, null, null, null, null);
        imageItemsList = getItemsList(cursor);
        cursor.close();
        db.close();
        return imageItemsList;
    }

    public synchronized static int getPendingImageCount(Context context) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getWritableDatabase();
        String selectQuery = "SELECT * FROM " + ImageSQLiteHelper.TABLE_IMAGE_ITEM + " WHERE " +
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " = " + ImageStatus.UPLOAD_PENDING + " OR " +
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " = " + ImageStatus.ERROR + " OR " +
                ImageSQLiteHelper.COL_UPLOAD_STATUS + " = " + ImageStatus.UPLOADING;
        Cursor cursor = db.rawQuery(selectQuery, null);
        int count = cursor.getCount();
        cursor.close();
        db.close();
        return count;
    }

    public static ArrayList<ImageItem> getItemsList(Cursor cursor) {
        ImageItem item;
        ArrayList<ImageItem> itemList = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                item = new ImageItem();
                item.imageLocalPath = cursor.getString(cursor.getColumnIndex(ImageSQLiteHelper.COL_IMAGE_LOCAL_PATH));
                item.bytesUploaded = cursor.getInt(cursor.getColumnIndex(ImageSQLiteHelper.COL_BYTES_UPLOADED));
                item.noChunksCompleted = cursor.getInt(cursor.getColumnIndex(ImageSQLiteHelper.COL_NO_COMPLETED_CHUNKS));
                item.entityType = cursor.getString(cursor.getColumnIndex(ImageSQLiteHelper.COL_ENTITY_TYPE));
                item.entityId = cursor.getInt(cursor.getColumnIndex(ImageSQLiteHelper.COL_ENTITY_ID));
                item.imageType = cursor.getString(cursor.getColumnIndex(ImageSQLiteHelper.COL_IMAGE_TYPE));
                item.s3Path = cursor.getString(cursor.getColumnIndex(ImageSQLiteHelper.COL_S3_PATH));
                item.status = cursor.getInt(cursor.getColumnIndex(ImageSQLiteHelper.COL_UPLOAD_STATUS));
                item.identifier = cursor.getString(cursor.getColumnIndex(ImageSQLiteHelper.COL_IDENTIFIER));
                item.timestamp = cursor.getLong(cursor.getColumnIndex(ImageSQLiteHelper.COL_TIMESTAMP));
                item.orderId = cursor.getInt(cursor.getColumnIndex(ImageSQLiteHelper.COL_ORDER_ID));
                itemList.add(item);
            } while (cursor.moveToNext());
        }
        return itemList;
    }

    public static synchronized List<ImageItem> getPurgeableImages(Context context, long timestamp) {
        ArrayList<ImageItem> itemsList;
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getReadableDatabase();

        Cursor cursor = db.query(ImageSQLiteHelper.TABLE_IMAGE_ITEM, null, ImageSQLiteHelper.COL_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(timestamp)}, null, null, null, null);
        itemsList = getItemsList(cursor);
        cursor.close();
        db.close();
        return itemsList;
    }

    public static synchronized int purgeImages(Context context, long timestamp) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getReadableDatabase();
        int count = db.delete(ImageSQLiteHelper.TABLE_IMAGE_ITEM, ImageSQLiteHelper.COL_TIMESTAMP + " <= ? ",
                new String[]{String.valueOf(timestamp)});
        db.close();
        return count;
    }

    public synchronized static void deleteImageByServerPath(Context context, String path) {
        SQLiteDatabase db = ImageSQLiteHelper.getInstance(context).getWritableDatabase();
        db.delete(ImageSQLiteHelper.TABLE_IMAGE_ITEM, ImageSQLiteHelper.COL_S3_PATH + " =?",
                new String[]{path});
        db.close();
    }

}
