package models.domain

data class OllamaConfig(
    val baseUrl: String,
    val models: List<LlmModel>,
)
