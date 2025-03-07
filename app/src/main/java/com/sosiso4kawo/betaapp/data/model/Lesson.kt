package com.sosiso4kawo.betaapp.data.model

data class Lesson(
    val uuid: String,
    val title: String,
    val description: String,
    val difficulty_id: Int,
    val difficulty: Difficulty,
    val order: Int,
    val course_uuid: String,
    val exercises: List<Any>? // Можно заменить на конкретную модель, если потребуется
)
