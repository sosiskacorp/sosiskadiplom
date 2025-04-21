package com.sosiso4kawo.zschoolapp.data.model

data class UpdateProfileRequest(
    val login: String?,
    val name: String?,
    val last_name: String?,
    val second_name: String?,
    val avatar: String?
)