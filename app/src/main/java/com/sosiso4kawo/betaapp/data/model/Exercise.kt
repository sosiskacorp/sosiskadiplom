package com.sosiso4kawo.betaapp.data.model

data class Exercise(
    val uuid: String,
    val title: String,
    val description: String,
    val points: Int,
    val order: Int,
    val lesson_uuid: String,
    val questions: List<Question>? = null
)
