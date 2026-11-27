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
    /** Facts about the user that answers are pitched at: background and skills. */
    val aboutUser: String,
    /** What the calls are about, such as their main technology or programming language; code is written in it. */
    val topic: String,
    /** The model that writes the fuller answers: they are asked for by hand, so a slower, stronger one fits. */
    val expansionModelId: String,
    val system: String,
    val user: String,
    val followUpsSystem: String,
    val followUpsUser: String,
    val expansionSystem: String,
    val expansionUser: String,
)

data class QuestionsSettingsNullable(
    val enabled: Boolean? = null,
    val modelId: String? = null,
    val phrases: Int? = null,
    val fullAnswers: Int? = null,
    val questionDisplayLanguage: String? = null,
    val answerDisplayLanguage: String? = null,
    val aboutUser: String? = null,
    val topic: String? = null,
    val expansionModelId: String? = null,
    val system: String? = null,
    val user: String? = null,
    val followUpsSystem: String? = null,
    val followUpsUser: String? = null,
    val expansionSystem: String? = null,
    val expansionUser: String? = null,
)
