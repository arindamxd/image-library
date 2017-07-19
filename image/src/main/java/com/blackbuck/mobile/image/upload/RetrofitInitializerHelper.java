package com.blackbuck.mobile.image.upload;

import android.content.Context;

import com.blackbuck.mobile.image.ImageInitializer;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/***
 * Created by dell on 15/2/16.
 */
public class RetrofitInitializerHelper {

    private static UploadServices uploadService;

    public static UploadServices getUploadServiceHelper(Context context){
        return uploadService == null ? getZinkaUplaodService(context) : uploadService;
    }

    private static UploadServices getZinkaUplaodService(Context context){
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ImageInitializer.getEndPoint(context))
                .client(builder.build())
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        uploadService = retrofit.create(UploadServices.class);
        return uploadService;
    }
}
