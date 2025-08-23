package models.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateWithGoogleTranslateResponse(
    @SerialName("data")
    val data: TranslationData
)

@Serializable
data class TranslationData(
    @SerialName("translations")
    val translations: List<Translation>
)

@Serializable
data class Translation(
    @SerialName("translatedText")
    val translatedText: String,
    @SerialName("detectedSourceLanguage")
    val detectedSourceLanguage: String? = null
)