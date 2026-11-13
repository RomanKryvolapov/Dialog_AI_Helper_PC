package models.domain

import models.common.TypeEnum

enum class ClaudeModelsEnum(
    override val type: String,
    val title: String,
    val shortTitle: String,
    val parameters: ModelParameterSupport,
) : TypeEnum {
    // Sonnet 5.5 refuses temperature, top P and top K, and thinking "disabled"; "between_tools" thinks only
    // between tool calls, and the app sends none, so it is thinking off. "adaptive" decides per request.
    CLAUDE_SONNET_5_5(
        type = "claude-sonnet-5-5",
        title = "Claude Sonnet 5.5",
        shortTitle = "Sonnet 5.5",
        parameters = ModelParameterSupport(
            thinkingLevels = listOf("between_tools", "adaptive"),
            defaultThinking = "between_tools",
        ),
    );

    fun getDescription() = "Claude: $type"
}
