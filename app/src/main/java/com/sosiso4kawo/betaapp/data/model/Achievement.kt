package com.sosiso4kawo.betaapp.data.model

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val secret: Boolean,
    // Поля для списка всех достижений
    val current_count: Int? = null,
    val achieved: Boolean? = null,
    val created_at: String,
    val achieved_at: String? = null,
    // Поле condition присутствует только в ответе для конкретного пользователя
    val condition: String? = null
)

data class Condition(
    val action: String? = null,
    val count: Int = 0,
    val timeframe: String? = null,
    val stat: String? = null,
    val top_percent: Int = 0,
    val action_seq: List<String>? = null,
    val secret: Boolean = false
)
