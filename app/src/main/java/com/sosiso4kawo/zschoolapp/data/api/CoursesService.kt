package com.sosiso4kawo.zschoolapp.data.api

import com.sosiso4kawo.zschoolapp.data.model.Course
import com.sosiso4kawo.zschoolapp.data.model.Lesson
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface CoursesService {
    @GET("v1/course/list")
    suspend fun getCourses(): Response<List<Course>>

    @GET("v1/course/{uuid}/content")
    suspend fun getCourseContent(@Path("uuid") uuid: String): Response<List<Lesson>>
}
