package com.sosiso4kawo.betaapp.data.model

data class Course(
    val uuid: String,
    val title: String,
    val description: String,
    val difficulty_id: Int,
    val difficulty: Difficulty
)

data class Difficulty(
    val id: Int,
    val title: String
)
