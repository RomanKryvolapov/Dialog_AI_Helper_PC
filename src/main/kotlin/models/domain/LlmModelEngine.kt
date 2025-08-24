package models.domain

import models.common.TypeEnum

enum class LlmModelEngine(
    override val type: String,
) : TypeEnum {
    LOCALHOST(
        type = "Localhost",
    ),
    GOOGLE(
        type = "Google",
    ),
    OLLAMA(
        type = "Ollama",
    )
}