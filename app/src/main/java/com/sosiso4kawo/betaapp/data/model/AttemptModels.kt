package com.sosiso4kawo.betaapp.data.model

import com.google.gson.annotations.SerializedName

data class StartAttemptResponse(
    @SerializedName("session_id") //
    val session_id: String
)

data class AnswerRequest(
    @SerializedName("question_id") val questionId: String,
    @SerializedName("answer") val answer: Any
)

data class FinishAttemptRequest(
    val correct_answers: Int,
    val is_finished: Boolean,
    val questions: Int
)

data class FinishAttemptResponse(
    val courses: List<CourseProgress>,
    val exercises: List<ExerciseProgress>,
    val lessons: List<LessonProgress>
)
