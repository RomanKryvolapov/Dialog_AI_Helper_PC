package models.domain

import models.common.TypeEnum

/**
 * The two cheapest current Claude models. [supportsTemperature] is false from Claude 5 onwards:
 * those models removed the sampling parameters and answer a request carrying one with a 400.
 */
enum class ClaudeModelsEnum(
    override val type: String,
    val supportsTemperature: Boolean,
) : TypeEnum {
    CLAUDE_SONNET_5(type = "claude-sonnet-5", supportsTemperature = false),
    CLAUDE_HAIKU_4_5(type = "claude-haiku-4-5", supportsTemperature = true);

    fun getDescription() = "Claude: $type"

    companion object {
        fun byType(type: String): ClaudeModelsEnum? = entries.firstOrNull { it.type == type }
    }
}
