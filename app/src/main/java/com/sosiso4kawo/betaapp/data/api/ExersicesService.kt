
package com.sosiso4kawo.betaapp.data.api

import com.sosiso4kawo.betaapp.data.model.Exercise
import com.sosiso4kawo.betaapp.data.model.Question
import com.sosiso4kawo.betaapp.data.model.CheckResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

interface ExercisesService {
    @GET("v1/exercise/{uuid}/info")
    suspend fun getExercise(@Path("uuid") uuid: String): Response<Exercise>

    @GET("v1/exercise/{uuid}/question")
    suspend fun getExerciseQuestions(@Path("uuid") uuid: String): Response<List<Question>>

    @GET("v1/question/{uuid}/info")
    suspend fun getQuestion(@Path("uuid") uuid: String): Response<Question>

    @POST("v1/question/{uuid}/check")
    suspend fun checkAnswer(@Path("uuid") uuid: String, @Body answer: Any): Response<CheckResponse>
}
