package com.sosiso4kawo.betaapp.data.model

import com.google.gson.annotations.SerializedName

data class Question(
    val uuid: String,
    val type_id: Int,
    val type: QuestionType,
    val text: String,
    val order: Int,
    val exercise_uuid: String,
    @SerializedName("question_options")
    val questionOptions: List<QuestionOption>? = null,
    val matching: Matching? = null
)

data class QuestionType(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String
)

data class QuestionOption(
    @SerializedName("uuid")
    val uuid: String,
    @SerializedName("text")
    val text: String
)

data class Matching(
    @SerializedName("left_side")
    val leftSide: List<String>?,
    @SerializedName("right_side")
    val rightSide: List<String>?
)

// Новая модель для проверки ответа
data class CheckResponse(
    val correct: Boolean,
    val message: String? = null
)

// Модели для отправки ответа в зависимости от типа вопроса
data class SingleAnswer(
    val answer: String
)

data class MultipleAnswer(
    val answer: List<String>
)

data class MatchingAnswer(
    val answer: Map<String, String>
)
