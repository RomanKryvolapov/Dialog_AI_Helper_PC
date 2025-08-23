package models.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateWithGoogleAiResponse(
    @SerialName("candidates")
    val candidates: List<Candidate>,
    @SerialName("usageMetadata")
    val usageMetadata: UsageMetadata,
    @SerialName("modelVersion")
    val modelVersion: String,
    @SerialName("responseId")
    val responseId: String
)

@Serializable
data class Candidate(
    @SerialName("content")
    val content: Content,
    @SerialName("finishReason")
    val finishReason: String,
    @SerialName("avgLogprobs")
    val avgLogprobs: Double
)

@Serializable
data class Content(
    @SerialName("parts")
    val parts: List<Part>,
    @SerialName("role")
    val role: String
)

@Serializable
data class Part(
    @SerialName("text")
    val text: String
)

@Serializable
data class UsageMetadata(
    @SerialName("promptTokenCount")
    val promptTokenCount: Int,
    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int,
    @SerialName("totalTokenCount")
    val totalTokenCount: Int,
    @SerialName("promptTokensDetails")
    val promptTokensDetails: List<TokenDetail>,
    @SerialName("candidatesTokensDetails")
    val candidatesTokensDetails: List<TokenDetail>
)

@Serializable
data class TokenDetail(
    @SerialName("modality")
    val modality: String,
    @SerialName("tokenCount")
    val tokenCount: Int
)
