package models.domain

/**
 * Everything a translation request is made of, as the user edits it on Settings → Main prompt and Voice
 * prompt. A field the user never changed holds the built-in text from Consts.kt, and is not saved (see
 * ApplicationInfoEnvOverlay.dropFallbackValues), so a better built-in text reaches it with the next version.
 */
data class PromptSettings(
    /** How many phrases said before the one being translated each request carries, for context only. */
    val previousPhrases: Int,
    /** Translating a phrase a speech recogniser wrote down (Main prompt). */
    val text: PromptParts,
    /** The running summary of the conversation, written by a request of its own (Main prompt). */
    val summary: SummarySettings,
    /** Translating a recorded phrase a voice model hears itself (Voice prompt). */
    val voice: PromptParts,
)

data class PromptParts(
    /** The instructions, sent as the system message. */
    val system: String,
    /** The message itself, with the placeholders filled in. */
    val user: String,
    /** How one earlier phrase is written into {{previous_phrases}}. */
    val previousPhrase: String,
)

data class SummarySettings(
    val enabled: Boolean,
    /** The summary is rewritten once this many phrases have left the window of previous phrases. */
    val everyPhrases: Int,
    val system: String,
    /** The phrases to fold in are written with the Main prompt's previous-phrase format. */
    val user: String,
)

data class PromptSettingsNullable(
    val previousPhrases: Int? = null,
    val text: PromptPartsNullable? = null,
    val summary: SummarySettingsNullable? = null,
    val voice: PromptPartsNullable? = null,
)

data class PromptPartsNullable(
    val system: String? = null,
    val user: String? = null,
    val previousPhrase: String? = null,
)

data class SummarySettingsNullable(
    val enabled: Boolean? = null,
    val everyPhrases: Int? = null,
    val system: String? = null,
    val user: String? = null,
)
