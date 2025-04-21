package com.sosiso4kawo.zschoolapp.data.model

data class Course(
    val uuid: String,
    val title: String,
    val description: String,
    // Опционально для детального запроса
    val type_id: Int? = null,
    val course_type: CourseType? = null,
    val difficulty_id: Int,
    val difficulty: Difficulty
)

data class Difficulty(
    val id: Int,
    val title: String
)

data class CourseType(
    val id: Int,
    val title: String
)
