package models.domain

import models.common.TypeEnum

enum class ClaudeModelsEnum(
    override val type: String,
    val title: String,
    val shortTitle: String,
    val thinkingOffType: String,
) : TypeEnum {
    // Sonnet 5.5 rejects "disabled"; "between_tools" thinks only between tool calls, and the app
    // sends none.
    CLAUDE_SONNET_5_5(
        type = "claude-sonnet-5-5",
        title = "Claude Sonnet 5.5",
        shortTitle = "Sonnet 5.5",
        thinkingOffType = "between_tools",
    );

    fun getDescription() = "Claude: $type"

    companion object {
        fun byType(type: String): ClaudeModelsEnum? = entries.firstOrNull { it.type == type }
    }
}
