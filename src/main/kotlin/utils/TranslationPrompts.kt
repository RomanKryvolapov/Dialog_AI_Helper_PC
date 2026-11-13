package utils

import VOICE_AUDIO_MARK
import models.domain.ConversationTurn
import models.domain.ModelPrompt
import models.domain.PromptSettings

/** The requests the app sends, built from the prompt settings and the conversation as it stands. */
object TranslationPrompts {

    /**
     * [canSummarize]: some method in the chain can write the summary (it reads text and has its key). Without
     * one, the phrases leaving the window are not kept for a summary that could never take them in.
     */
    fun contextSettings(prompts: PromptSettings, canSummarize: Boolean) = ConversationContext.Settings(
        window = prompts.previousPhrases,
        summarize = prompts.summary.enabled && canSummarize,
        summaryEvery = prompts.summary.everyPhrases,
    )

    /** What {{summary}} carries in a request: the summary as it stands, or nothing while it is switched off. */
    fun summaryInRequest(prompts: PromptSettings, summary: String): String =
        if (prompts.summary.enabled) summary else ""

    /** Translating [phrase], as a speech recogniser wrote it down. */
    fun text(prompts: PromptSettings, context: ConversationContext.Snapshot, phrase: String) = ModelPrompt(
        system = prompts.text.system,
        user = fillTemplate(
            prompts.text.user,
            mapOf(
                PromptVariables.SUMMARY to summaryInRequest(prompts, context.summary),
                PromptVariables.PREVIOUS_PHRASES to phrases(prompts.text.previousPhrase, context.previous(prompts.previousPhrases)),
                PromptVariables.PHRASE to phrase,
            ),
        ),
    )

    /** Translating a recorded phrase the model hears itself; the recording goes where [VOICE_AUDIO_MARK] stands. */
    fun voice(prompts: PromptSettings, context: ConversationContext.Snapshot) = ModelPrompt(
        system = prompts.voice.system,
        user = fillTemplate(
            prompts.voice.user,
            mapOf(
                PromptVariables.SUMMARY to summaryInRequest(prompts, context.summary),
                PromptVariables.PREVIOUS_PHRASES to phrases(prompts.voice.previousPhrase, context.previous(prompts.previousPhrases)),
                PromptVariables.AUDIO to VOICE_AUDIO_MARK,
            ),
        ),
    )

    /** Folding [fold]'s phrases into the summary; they are written in the Main prompt's previous-phrase format. */
    fun summary(prompts: PromptSettings, fold: ConversationContext.Fold) = ModelPrompt(
        system = prompts.summary.system,
        user = fillTemplate(
            prompts.summary.user,
            mapOf(
                PromptVariables.SUMMARY to fold.summary,
                PromptVariables.NEW_PHRASES to phrases(prompts.text.previousPhrase, fold.turns),
            ),
        ),
    )

    /** [turns] written one after another in [format]. */
    fun phrases(format: String, turns: List<ConversationTurn>): String =
        turns.joinToString("\n") { turn ->
            fillTemplate(
                format,
                mapOf(PromptVariables.SAID to turn.source, PromptVariables.TRANSLATED to turn.translation.orEmpty()),
            )
        }
}
