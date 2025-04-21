package com.sosiso4kawo.zschoolapp.data.model

data class LeaderboardUser(
    val user_uuid: String,
    val login: String?,
    val name: String?,
    val last_name: String?,
    val second_name: String?,
    val avatar: String?, // URL аватара
    val rank: Int?,
    val total_points: Int?
)