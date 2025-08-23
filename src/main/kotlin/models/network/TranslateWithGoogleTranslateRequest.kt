package models.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateWithGoogleTranslateRequest(
    @SerialName("q")
    val q: List<String>,
    @SerialName("source")
    val source: String?,
    @SerialName("target")
    val target: String,
    @SerialName("format")
    val format: String = "text"
)
