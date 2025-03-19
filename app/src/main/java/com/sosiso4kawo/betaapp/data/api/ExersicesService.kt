package com.sosiso4kawo.betaapp.data.api

import com.sosiso4kawo.betaapp.data.model.AnswerRequest
import com.sosiso4kawo.betaapp.data.model.Exercise
import com.sosiso4kawo.betaapp.data.model.Question
import com.sosiso4kawo.betaapp.data.model.CheckResponse
import com.sosiso4kawo.betaapp.data.model.FinishAttemptRequest
import com.sosiso4kawo.betaapp.data.model.FinishAttemptResponse
import com.sosiso4kawo.betaapp.data.model.StartAttemptResponse
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

    @POST("v1/attempts/start/{exerciseUuid}")
    suspend fun startAttempt(
        @Path("exerciseUuid") exerciseUuid: String
    ): Response<StartAttemptResponse>

    @POST("v1/attempts/{sessionId}/answer")
    suspend fun submitAnswer(
        @Path("sessionId") sessionId: String,
        @Body answerRequest: AnswerRequest
    ): Response<CheckResponse>

    @POST("v1/attempts/{sessionId}/finish")
    suspend fun finishAttempt(
        @Path("sessionId") sessionId: String,
        @Body finishAttempt: FinishAttemptRequest
    ): Response<FinishAttemptResponse>
}
