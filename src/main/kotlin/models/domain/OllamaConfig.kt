package models.domain

data class OllamaConfig(
    val ip: String,
    val port: String,
    val models: List<LlmModel>,
)
