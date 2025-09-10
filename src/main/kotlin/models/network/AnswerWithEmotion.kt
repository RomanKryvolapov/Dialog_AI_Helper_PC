package models.network

import kotlinx.serialization.Serializable

@Serializable
data class AnswerWithEmotion(
    val answer: String,
    val emotion: String
)