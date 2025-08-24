package models.domain

/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
data class LlmModel(
    val id: String,
    val description: String?,
    val engine: LlmModelEngine,
    val googleAiModel: GoogleAiModelsEnum?,
)