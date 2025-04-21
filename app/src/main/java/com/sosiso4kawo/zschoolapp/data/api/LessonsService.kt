package com.sosiso4kawo.zschoolapp.data.api

import com.sosiso4kawo.zschoolapp.data.model.Exercise
import com.sosiso4kawo.zschoolapp.data.model.LessonInfo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface LessonsService {
    @GET("v1/lesson/{uuid}/info")
    suspend fun getLessonInfo(@Path("uuid") uuid: String): Response<LessonInfo>

    // Возвращает список упражнений, являющихся контентом урока
    @GET("v1/lesson/{uuid}/content")
    suspend fun getLessonContent(@Path("uuid") uuid: String): Response<List<Exercise>>
}
