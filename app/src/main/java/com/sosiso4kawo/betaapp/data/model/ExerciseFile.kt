package com.sosiso4kawo.betaapp.data.model

import com.google.gson.annotations.SerializedName

data class ExerciseFile(
    @SerializedName("uuid")
    val uuid: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("file_url")
    val fileUrl: String,

    @SerializedName("exercise_uuid")
    val exerciseUuid: String
)