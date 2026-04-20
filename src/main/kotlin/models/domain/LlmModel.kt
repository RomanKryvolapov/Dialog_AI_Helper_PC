package models.domain

data class LlmModel(
    val id: String,
    val description: String?,
    val engine: LlmModelEngine,
    val googleAiModel: GoogleAiModelsEnum? = null,
    val openAiModel: OpenAiModelsEnum? = null,
    val claudeModel: ClaudeModelsEnum? = null,
)
