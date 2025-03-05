package com.sosiso4kawo.betaapp.data.api

import com.sosiso4kawo.betaapp.data.model.Achievement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface AchievementsService {
    @GET("v1/achievements/list")
    suspend fun getAchievements(
        @Header("Authorization") token: String
    ): Response<AchievementsResponse>

    @GET("v1/users/achievements/{uuid}")
    suspend fun getUserAchievements(
        @Path("uuid") uuid: String,
        @Header("Authorization") token: String
    ): Response<AchievementsResponse>
}

data class AchievementsResponse(
    val achievements: List<Achievement>
)
