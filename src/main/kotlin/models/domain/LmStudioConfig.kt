package models.domain

data class LmStudioConfig(
    val ip: String,
    val port: String,
    val models: List<LlmModel>,
)
