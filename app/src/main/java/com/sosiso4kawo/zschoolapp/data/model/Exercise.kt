package com.sosiso4kawo.zschoolapp.data.model

import com.google.gson.annotations.SerializedName

data class Exercise(
    val uuid: String,
    val title: String,
    val description: String,
    val points: Int,
    val order: Int,

    @SerializedName("lesson_uuid")
    val lessonUuid: String,

    val questions: List<Question>? = null,

    @SerializedName("exercise_files")
    val exerciseFiles: List<ExerciseFile>? = null
)