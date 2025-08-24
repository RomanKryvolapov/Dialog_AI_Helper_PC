/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package models.network.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaModelsResponse(
    @SerialName("models")
    val models: List<OllamaModelResponse> = emptyList()
)

@Serializable
data class OllamaModelResponse(
    @SerialName("name")
    val name: String? = null,

    @SerialName("model")
    val model: String? = null,

    @SerialName("modified_at")
    val modifiedAt: String? = null,

    @SerialName("size")
    val size: Long? = null,

    @SerialName("digest")
    val digest: String? = null,

    @SerialName("details")
    val details: OllamaModelDetailsResponse? = null,
)

@Serializable
data class OllamaModelDetailsResponse(
    @SerialName("parent_model")
    val parentModel: String? = null,

    @SerialName("format")
    val format: String? = null,

    @SerialName("family")
    val family: String? = null,

    @SerialName("families")
    val families: List<String>? = null,

    @SerialName("parameter_size")
    val parameterSize: String? = null,

    @SerialName("quantization_level")
    val quantizationLevel: String? = null,
)
