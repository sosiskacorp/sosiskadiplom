package com.sosiso4kawo.betaapp.data.model

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val secret: Boolean,
    // Поля для списка всех достижений
    val current_count: Int?,
    val achieved: Boolean?,
    val created_at: String,
    val achieved_at: String?,
    // Поле condition присутствует только в ответе для конкретного пользователя
    val condition: Condition?
)

data class Condition(
    val count: Int,
    val action: String?
)
