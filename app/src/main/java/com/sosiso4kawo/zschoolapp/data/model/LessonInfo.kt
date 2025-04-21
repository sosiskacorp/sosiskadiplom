package com.sosiso4kawo.zschoolapp.data.model

data class LessonInfo(
    val uuid: String,
    val title: String,
    val description: String,
    // Дополнительные поля, если сервер их возвращает
    val duration: Int? = null,
    val prerequisites: String? = null
)
