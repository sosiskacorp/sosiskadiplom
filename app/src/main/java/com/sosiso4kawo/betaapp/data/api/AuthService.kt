package com.sosiso4kawo.betaapp.data.api

import com.sosiso4kawo.betaapp.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthService {
    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>

    @POST("v1/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<Void>

    @GET("v1/users/me")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    @POST("v1/auth/verification/code")
    suspend fun sendVerificationCode(
        @Query("email") email: String
    ): Response<Unit>

    // Новый эндпоинт для подтверждения почты
    @POST("v1/auth/verification/email")
    suspend fun verifyEmail(
        @Body request: VerificationRequest
    ): Response<Unit>
}
