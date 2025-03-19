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

data class CourseProgress(
    val uuid: String,
    val course_uuid: String,
    val total_points: Int,
    val completed_at: String
)

data class ExerciseProgress(
    val uuid: String,
    val exercise_uuid: String,
    val total_points: Int,
    val completed_at: String
)

data class LessonProgress(
    val uuid: String,
    val lesson_uuid: String,
    val total_points: Int,
    val completed_at: String
)