package utils

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage
import dev.langchain4j.model.output.TokenUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Tokens the current dialog's model requests — translations and conversation summaries — have used, as
 * each provider reports them with a finished answer. Cleared with the dialog (Clear dialog); an answer to a
 * request sent before that is not counted in the new dialog.
 */
class TokenUsageTracker {

    private val lock = Any()

    /** Which dialog a request belongs to; [reset] starts the next one. */
    private var dialog = 0L

    data class ModelUsage(val input: Long = 0, val output: Long = 0, val answers: Int = 0)

    data class Totals(
        val byModel: Map<String, ModelUsage> = emptyMap(),
        /** Finished answers whose provider sent no usage. */
        val unreported: Int = 0,
    ) {
        val input: Long get() = byModel.values.sumOf { it.input }
        val output: Long get() = byModel.values.sumOf { it.output }
        val total: Long get() = input + output
        val answers: Int get() = byModel.values.sumOf { it.answers }
    }

    private val state = MutableStateFlow(Totals())

    val totals: StateFlow<Totals> = state

    /** The dialog a request starting now belongs to; hand it back to [record] with the answer. */
    fun currentDialog(): Long = synchronized(lock) { dialog }

    /**
     * One finished answer of [model], to a request sent in [dialog]. Gemini reports its thinking apart from
     * the answer; it is billed as output, so it is counted as output here too (OpenAI and Anthropic already
     * include theirs).
     */
    fun record(model: String, usage: TokenUsage?, dialog: Long) = synchronized(lock) {
        if (dialog != this.dialog) return@synchronized
        state.update { totals ->
            if (usage == null) return@update totals.copy(unreported = totals.unreported + 1)
            val thoughts = (usage as? GoogleAiGeminiTokenUsage)?.thoughtsTokenCount() ?: 0
            val before = totals.byModel[model] ?: ModelUsage()
            val after = before.copy(
                input = before.input + (usage.inputTokenCount() ?: 0),
                output = before.output + (usage.outputTokenCount() ?: 0) + thoughts,
                answers = before.answers + 1,
            )
            totals.copy(byModel = totals.byModel + (model to after))
        }
    }

    fun reset() = synchronized(lock) {
        dialog += 1
        state.value = Totals()
    }
}
