package models.domain

data class LmStudioConfig(
    val baseUrl: String,
    val models: List<LlmModel>,
)
