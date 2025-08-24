/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package models.network.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIGenerationRequest(
    @SerialName("model")
    val model: String,

    @SerialName("stream")
    val stream: Boolean,

    @SerialName("messages")
    val messages: List<OpenAIGenerationRequestMessage>,
)

@Serializable
data class OpenAIGenerationRequestMessage(
    @SerialName("role")
    val role: String,

    @SerialName("content")
    val content: String,
)
