package models.domain

/** Settings → Questions: the mode that answers the questions asked in the conversation (Questions screen). */
data class QuestionsSettings(
    /** On: every phrase heard also goes to the questions request (the "Questions" box in the top bar). */
    val enabled: Boolean,
    /** The model that finds and answers the questions, chosen apart from the translation methods. */
    val modelId: String,
    /** How many of the latest phrases each request carries; a question often spans several. */
    val phrases: Int,
    /** How many of the latest answers the history keeps in full; older entries keep only their question. */
    val fullAnswers: Int,
    /**
     * The languages the Questions screen shows a question and its answer in, as English names ("Russian"); blank is
     * the language of the call. They fill the instructions' placeholders (see `QuestionPrompts.languageFor`).
     */
    val questionDisplayLanguage: String,
    val answerDisplayLanguage: String,
    val system: String,
    val user: String,
)

data class QuestionsSettingsNullable(
    val enabled: Boolean? = null,
    val modelId: String? = null,
    val phrases: Int? = null,
    val fullAnswers: Int? = null,
    val questionDisplayLanguage: String? = null,
    val answerDisplayLanguage: String? = null,
    val system: String? = null,
    val user: String? = null,
)
