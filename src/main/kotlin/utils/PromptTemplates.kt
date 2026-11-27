package utils

/**
 * The placeholders the prompt parts are written with. Each part may use only its own set (see [PromptPart]);
 * the Settings screens refuse any other, so a request never carries a placeholder nobody fills in.
 */
object PromptVariables {
    const val SUMMARY = "summary"
    const val PREVIOUS_PHRASES = "previous_phrases"
    const val PHRASE = "phrase"
    const val AUDIO = "audio"
    const val NEW_PHRASES = "new_phrases"
    const val SAID = "said"
    const val TRANSLATED = "translated"
    const val ANSWERED = "answered"
    const val SPEECH = "speech"
    const val QUESTION_LANGUAGE = "question_language"
    const val ANSWER_LANGUAGE = "answer_language"
    const val ABOUT_USER = "about_user"
    const val TOPIC = "topic"
    const val QUESTION = "question"
    const val ANSWER = "answer"
    const val FOLLOW_UP = "follow_up"
    const val HEARD = "heard"

    val descriptions: Map<String, String> = linkedMapOf(
        SUMMARY to "the running summary of the conversation, empty until the first one is written",
        PREVIOUS_PHRASES to "the phrases said just before, oldest first, each in the previous-phrase format",
        PHRASE to "the phrase to translate, as the speech recogniser wrote it down",
        AUDIO to "where the recording of the phrase goes in the message",
        NEW_PHRASES to "the phrases to fold into the summary, oldest first, in the previous-phrase format",
        SAID to "what was said",
        TRANSLATED to "the translation given for it, empty while it is still being translated",
        ANSWERED to "the questions found so far in this dialog with their answers, oldest first (only the question for older ones)",
        SPEECH to "the latest phrases, oldest first, one per line; \"S1: \" before each when the recogniser tells speakers apart",
        QUESTION_LANGUAGE to "the language chosen above for showing the question (\"the language of the call\" when none is chosen)",
        ANSWER_LANGUAGE to "the language chosen above for showing the answer",
        ABOUT_USER to "About me: the user's background and skills, \"(not given)\" when empty",
        TOPIC to "the topic of the calls, such as the language code is written in, \"(not given)\" when empty",
        QUESTION to "the question the answer was given to",
        ANSWER to "the short answer suggested for it",
        FOLLOW_UP to "the follow-up question the user picked",
        HEARD to "whose speech the transcript holds: both sides (speaker labels), or the other side with the user perhaps mixed in",
    )
}

/** One editable part of a prompt: which placeholders it may use and which it cannot do without. */
enum class PromptPart(val allowed: Set<String>, val required: Set<String> = emptySet()) {
    TEXT_MESSAGE(
        setOf(PromptVariables.SUMMARY, PromptVariables.PREVIOUS_PHRASES, PromptVariables.PHRASE),
        required = setOf(PromptVariables.PHRASE),
    ),
    VOICE_MESSAGE(setOf(PromptVariables.SUMMARY, PromptVariables.PREVIOUS_PHRASES, PromptVariables.AUDIO)),
    SUMMARY_MESSAGE(
        setOf(PromptVariables.SUMMARY, PromptVariables.NEW_PHRASES),
        required = setOf(PromptVariables.NEW_PHRASES),
    ),
    PREVIOUS_PHRASE(setOf(PromptVariables.SAID, PromptVariables.TRANSLATED), required = setOf(PromptVariables.SAID)),
    QUESTIONS_MESSAGE(
        setOf(
            PromptVariables.ABOUT_USER, PromptVariables.TOPIC, PromptVariables.SUMMARY, PromptVariables.ANSWERED,
            PromptVariables.HEARD, PromptVariables.SPEECH,
        ),
        required = setOf(PromptVariables.SPEECH),
    ),
    FOLLOW_UPS_MESSAGE(
        setOf(PromptVariables.ABOUT_USER, PromptVariables.TOPIC, PromptVariables.SUMMARY, PromptVariables.QUESTION, PromptVariables.ANSWER),
        required = setOf(PromptVariables.QUESTION, PromptVariables.ANSWER),
    ),
    EXPANSION_MESSAGE(
        setOf(
            PromptVariables.ABOUT_USER, PromptVariables.TOPIC, PromptVariables.SUMMARY, PromptVariables.QUESTION,
            PromptVariables.ANSWER, PromptVariables.FOLLOW_UP,
        ),
        required = setOf(PromptVariables.QUESTION, PromptVariables.FOLLOW_UP),
    ),

    /** The follow-up and fuller-answer instructions: written in the answer language, which is a setting of its own. */
    ANSWER_LANGUAGE_INSTRUCTIONS(setOf(PromptVariables.ANSWER_LANGUAGE), required = setOf(PromptVariables.ANSWER_LANGUAGE)),

    /** The questions instructions: the two display languages are settings of their own, so they come in by placeholder. */
    QUESTIONS_INSTRUCTIONS(
        setOf(PromptVariables.QUESTION_LANGUAGE, PromptVariables.ANSWER_LANGUAGE),
        required = setOf(PromptVariables.QUESTION_LANGUAGE, PromptVariables.ANSWER_LANGUAGE),
    ),

    /** The instructions: sent the same with every request, so they take no placeholders. */
    INSTRUCTIONS(emptySet()),
}

private val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z0-9_]+)\s*}}""")

/**
 * [template] with each {{name}} that [values] has replaced by its value, in a single pass: text that came in
 * with a value is never searched for placeholders again.
 */
fun fillTemplate(template: String, values: Map<String, String>): String =
    PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }

/** Why [text] cannot be saved as [part], or null when it can. */
fun promptProblem(part: PromptPart, text: String): String? {
    val used = PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toSet()
    val unknown = used - part.allowed
    val missing = part.required - used
    return when {
        text.isBlank() -> null
        unknown.isNotEmpty() && part.allowed.isEmpty() ->
            "The instructions take no placeholders: ${unknown.joinToString { "{{$it}}" }}"
        unknown.isNotEmpty() ->
            "Unknown placeholder ${unknown.joinToString { "{{$it}}" }}; this part takes ${part.allowed.joinToString { "{{$it}}" }}"
        missing.isNotEmpty() ->
            "${missing.joinToString { "{{$it}}" }} is missing: without it the model never gets that input"
        else -> null
    }
}
