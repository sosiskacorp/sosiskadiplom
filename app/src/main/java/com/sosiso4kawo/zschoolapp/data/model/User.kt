package com.sosiso4kawo.zschoolapp.data.model

data class User(
    val uuid: String,
    val login: String?,
    val name: String?,
    val last_name: String?,
    val second_name: String?,
    val avatar: String?, // URL аватара
    val total_points: Int,
    val finished_courses: Int
)