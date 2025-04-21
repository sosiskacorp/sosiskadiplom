package com.sosiso4kawo.zschoolapp.data.model

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
    @SerializedName("matching")
    val matching: Matching? = null,
    val images: List<QuestionImage>? = null
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

data class QuestionImage(
    @SerializedName("image_url")
    val imageUrl: String
)

// Новая модель для проверки ответа
data class CheckResponse(
    @SerializedName("is_correct")
    val correct: Boolean,
    val message: String? = null
)
