package com.sosiso4kawo.zschoolapp.data.api

import com.sosiso4kawo.zschoolapp.data.model.AnswerRequest
import com.sosiso4kawo.zschoolapp.data.model.Exercise
import com.sosiso4kawo.zschoolapp.data.model.Question
import com.sosiso4kawo.zschoolapp.data.model.CheckResponse
import com.sosiso4kawo.zschoolapp.data.model.FinishAttemptRequest
import com.sosiso4kawo.zschoolapp.data.model.FinishAttemptResponse
import com.sosiso4kawo.zschoolapp.data.model.StartAttemptResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface ExercisesService {
    @GET("v1/exercise/{uuid}/info")
    suspend fun getExercise(@Path("uuid") uuid: String): Response<Exercise>

    @GET("v1/exercise/{uuid}/question")
    suspend fun getExerciseQuestions(@Path("uuid") uuid: String): Response<List<Question>>

    @GET("v1/question/{uuid}/info")
    suspend fun getQuestion(@Path("uuid") uuid: String): Response<Question>

    @POST("v1/attempts/start/{exerciseUuid}")
    suspend fun startAttempt(
        @Path("exerciseUuid") exerciseUuid: String
    ): Response<StartAttemptResponse>

    @POST("v1/attempts/answer")
    suspend fun submitAnswer(
        @Query("session_id") sessionId: String,
        @Body answerRequest: AnswerRequest
    ): Response<CheckResponse>

    @POST("v1/attempts/finish")
    suspend fun finishAttempt(
        @Query("session_id") sessionId: String,
        @Body finishAttempt: FinishAttemptRequest
    ): Response<FinishAttemptResponse>
}
