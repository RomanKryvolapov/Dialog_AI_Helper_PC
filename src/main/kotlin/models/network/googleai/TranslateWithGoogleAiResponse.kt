package models.network.googleai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateWithGoogleAiResponse(
    @SerialName("candidates")
    val candidates: List<Candidate> = emptyList(),

    @SerialName("usageMetadata")
    val usageMetadata: UsageMetadata? = null,

    @SerialName("modelVersion")
    val modelVersion: String? = null,

    @SerialName("responseId")
    val responseId: String? = null
)

@Serializable
data class Candidate(
    @SerialName("content")
    val content: Content? = null,

    @SerialName("finishReason")
    val finishReason: String? = null,

    @SerialName("avgLogprobs")
    val avgLogprobs: Double? = null
)

@Serializable
data class Content(
    @SerialName("parts")
    val parts: List<Part> = emptyList(),

    @SerialName("role")
    val role: String? = null
)

@Serializable
data class Part(
    @SerialName("text")
    val text: String? = null
)

@Serializable
data class UsageMetadata(
    @SerialName("promptTokenCount")
    val promptTokenCount: Int? = null,

    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int? = null,

    @SerialName("totalTokenCount")
    val totalTokenCount: Int? = null,

    @SerialName("promptTokensDetails")
    val promptTokensDetails: List<TokenDetail> = emptyList(),

    @SerialName("candidatesTokensDetails")
    val candidatesTokensDetails: List<TokenDetail> = emptyList()
)

@Serializable
data class TokenDetail(
    @SerialName("modality")
    val modality: String? = null,

    @SerialName("tokenCount")
    val tokenCount: Int? = null
)
