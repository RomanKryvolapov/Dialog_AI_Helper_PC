/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package models.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIGenerationResponse(
    @SerialName("id")
    val id: String? = null,

    @SerialName("object")
    val objectMessage: String? = null,

    @SerialName("created")
    val created: Long? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("system_fingerprint")
    val systemFingerprint: String? = null,

    @SerialName("choices")
    val choices: List<OpenAIGenerationResponseChoice> = emptyList(),
)

@Serializable
data class OpenAIGenerationResponseChoice(
    @SerialName("index")
    val index: Long? = null,

    @SerialName("message")
    val message: OpenAIGenerationResponseMessage? = null,

    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class OpenAIGenerationResponseMessage(
    @SerialName("role")
    val role: String? = null,

    @SerialName("content")
    val content: String? = null,
)
