package models.network.googleai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateWithGoogleAiRequest(
    @SerialName("contents")
    val contents: List<ContentRequest>
)

@Serializable
data class ContentRequest(
    @SerialName("role")
    val role: String,
    @SerialName("parts")
    val parts: List<PartRequest>
)

@Serializable
data class PartRequest(
    @SerialName("text")
    val text: String
)
