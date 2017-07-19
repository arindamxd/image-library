package com.blackbuck.mobile.image.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.blackbuck.mobile.image.BuildConfig;

/***
 * Created by Ankit Aggarwal on 11/7/16.
 */
public class ImageSQLiteHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "uploaditem.db";

    private static final int DB_VERSION = BuildConfig.IMAGE_DB_VERSION;
    private static final String TAG = "ImageSQLiteHelper";

    private static ImageSQLiteHelper mInstance;
    private Context context;

    public static final String TABLE_IMAGE_ITEM = "opsimage";
    public static final String COL_IMAGE_LOCAL_PATH = "local_image_path";
    public static final String COL_BYTES_UPLOADED = "bytes_uploaded";
    public static final String COL_NO_COMPLETED_CHUNKS = "no_completed_chunks";
    public static final String COL_ENTITY_TYPE = "entity_type";
    public static final String COL_ENTITY_ID = "entity_id";
    public static final String COL_IMAGE_TYPE = "image_type";
    public static final String COL_S3_PATH = "s3_path";
    public static final String COL_UPLOAD_STATUS = "upload_status";
    public static final String COL_IDENTIFIER = "identifier";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_ORDER_ID = "order_id";

    public static final String CREATE_TABLE_IMAGE_ITEM = "CREATE TABLE IF NOT EXISTS " + TABLE_IMAGE_ITEM + " ("
            + COL_IMAGE_LOCAL_PATH + " TEXT, "
            + COL_BYTES_UPLOADED + " INTEGER NOT NULL, "
            + COL_NO_COMPLETED_CHUNKS + " INTEGER NOT NULL DEFAULT 0, "
            + COL_ENTITY_TYPE + " INTEGER, "
            + COL_ENTITY_ID + " INTEGER  NOT NULL, "
            + COL_IMAGE_TYPE + " INTEGER, "
            + COL_S3_PATH + " TEXT PRIMARY KEY , "
            + COL_IDENTIFIER + " TEXT, "
            + COL_TIMESTAMP + " INTEGER, "
            + COL_ORDER_ID + " INTEGER, "
            + COL_UPLOAD_STATUS + " INTEGER)";



    public static final String DROP_TBL_IMAGE_ITEM = "DROP TABLE IF EXISTS " + TABLE_IMAGE_ITEM;

    public static ImageSQLiteHelper getInstance(Context context) {
        return mInstance == null ? new ImageSQLiteHelper(context) : mInstance;
    }

    private ImageSQLiteHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mInstance = this;
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_IMAGE_ITEM);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion == 11)return;
        db.execSQL("ALTER TABLE " + TABLE_IMAGE_ITEM + " ADD COLUMN " + COL_ORDER_ID + " INTEGER ");
        Log.i(TAG, "image table successfully upgraded and migrated, ");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(ImageSQLiteHelper.DROP_TBL_IMAGE_ITEM);
        onCreate(db);
    }
}
