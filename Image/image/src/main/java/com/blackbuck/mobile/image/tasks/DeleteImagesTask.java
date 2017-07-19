package com.blackbuck.mobile.image.tasks;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.blackbuck.mobile.image.utils.ImageUtils;

/**
 * Created by ankit on 26/06/17.
 */

public class DeleteImagesTask extends AsyncTask<String, Void, Void> {
    private static final String TAG = DeleteImagesTask.class.getName();

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
