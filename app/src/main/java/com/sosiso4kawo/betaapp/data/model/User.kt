package com.sosiso4kawo.betaapp.data.model

data class User(
    val uuid: String,
    val name: String?,
    val last_name: String?,
    val second_name: String?,
    val avatar: String? // URL аватара
)