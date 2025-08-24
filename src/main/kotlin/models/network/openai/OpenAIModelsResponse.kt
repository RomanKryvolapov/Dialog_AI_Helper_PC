package models.network.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIModelsResponse(
    @SerialName("data")
    val data: List<OpenAIModelResponse> = emptyList()
)

@Serializable
data class OpenAIModelResponse(
    @SerialName("id")
    val id: String? = null,

    @SerialName("object")
    val modelObject: String? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("publisher")
    val publisher: String? = null,

    @SerialName("arch")
    val arch: String? = null,

    @SerialName("compatibility_type")
    val compatibilityType: String? = null,

    @SerialName("quantization")
    val quantization: String? = null,

    @SerialName("state")
    val state: String? = null,

    @SerialName("max_context_length")
    val maxContextLength: Long? = null,

    @SerialName("loaded_context_length")
    val loadedContextLength: Long? = null
)
