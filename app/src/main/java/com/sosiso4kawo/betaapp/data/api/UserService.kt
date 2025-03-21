package com.sosiso4kawo.betaapp.data.api

import com.sosiso4kawo.betaapp.data.model.UpdateProfileRequest
import com.sosiso4kawo.betaapp.data.model.User
import com.sosiso4kawo.betaapp.data.model.UsersResponse
import com.sosiso4kawo.betaapp.data.model.ProgressResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface UserService {
    @GET("v1/users/me")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    @PATCH("v1/users/me")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileRequest
    ): Response<Void>

    @Multipart
    @POST("v1/users/me/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<Void>

    @GET("v1/users/all")
    suspend fun getAllUsers(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<UsersResponse>

    @GET("v1/users/{uuid}")
    suspend fun getUserByUuid(
        @Path("uuid") uuid: String,
        @Header("Authorization") token: String
    ): Response<User>

    // Новый метод для получения прогресса пользователя
    @GET("v1/users/me/progress")
    suspend fun getProgress(@Header("Authorization") token: String): Response<ProgressResponse>
}
