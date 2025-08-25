package models.domain

import models.common.TypeEnum

enum class LlmModelEngine(
    override val type: String,
) : TypeEnum {
    GOOGLE(
        type = "Google",
    ),
    LM_STUDIO(
        type = "LM Studio",
    ),
    OLLAMA(
        type = "Ollama",
    )
}