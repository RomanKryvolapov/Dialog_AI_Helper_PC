package models.domain

import models.common.TypeEnum

enum class ClaudeModelsEnum(
    override val type: String,
) : TypeEnum {
    CLAUDE_HAIKU_4_5(type = "claude-haiku-4-5");

    fun getDescription() = "Claude: $type"
}
