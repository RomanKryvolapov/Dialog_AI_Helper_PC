package models.domain

/**
 * The request settings the user chose for one model (Settings → LLM); a null field takes the model's
 * default. What is actually sent is [effectiveParameters]: a model never gets a setting it refuses.
 */
data class ModelParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    /** Thinking level (Gemini, Claude) or reasoning effort (OpenAI), in the provider's own words. */
    val thinking: String? = null,
    val verbosity: String? = null,
)

/**
 * What a request to one model may carry, and what it gets when the user sets nothing. Each value was sent
 * to Google and OpenAI in test requests on 2026-11-09; Claude's come from Anthropic's documentation, as no
 * Claude key was at hand to test with.
 */
data class ModelParameterSupport(
    val temperature: Boolean = false,
    val topP: Boolean = false,
    val topK: Boolean = false,
    /** The levels the user may pick, lightest first; empty when the model takes no choice of level. */
    val thinkingLevels: List<String> = emptyList(),
    /** Sent when the user picked no level (or the model takes none but this one); null sends no setting. */
    val defaultThinking: String? = null,
    val verbosityLevels: List<String> = emptyList(),
    /** The only thinking level at which the model accepts temperature and top P (GPT-6.1 Terra: "none"). */
    val samplingOnlyAtThinking: String? = null,
    val defaultTemperature: Double? = null,
)

/** What a request carries: the [chosen] settings the model accepts, and its defaults for the rest. */
fun effectiveParameters(support: ModelParameterSupport, chosen: ModelParameters?): ModelParameters {
    val thinking = chosen?.thinking?.takeIf { it in support.thinkingLevels } ?: support.defaultThinking
    val sampling = support.samplingOnlyAtThinking == null || thinking == support.samplingOnlyAtThinking
    return ModelParameters(
        temperature = (chosen?.temperature ?: support.defaultTemperature).takeIf { support.temperature && sampling },
        topP = chosen?.topP.takeIf { support.topP && sampling },
        topK = chosen?.topK.takeIf { support.topK },
        maxOutputTokens = chosen?.maxOutputTokens,
        thinking = thinking,
        verbosity = chosen?.verbosity?.takeIf { it in support.verbosityLevels },
    )
}
