package com.blackbuck.mobile.image.upload;

import android.content.Intent;

import com.blackbuck.mobile.image.BuildConfig;
import com.blackbuck.mobile.image.UploadResponse;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;

/**
 * Created by dell on 30/1/16.
 */
public interface UploadServices {

    @Multipart
    @POST
    Call<UploadResponse> uploadChunkToServer(@Url String url,
                                             @Header("Authorization") String authorization,
                                             @Part("img_chunk\"; filename=\"image") RequestBody file,
                                             @Part("session_key") String sessionKey,
                                             @Part("is_final") Integer isFinal,
                                             @Part("s3_path") String s3Path,
                                             @Part("sequence_id") Integer sequenceId,
                                             @Part("entity_type") String entityType,
                                             @Part("entity_id") Integer entityId,
                                             @Part("image_type") String imageType);
}
