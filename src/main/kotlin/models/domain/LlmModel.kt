package models.domain

data class LlmModel(
    val id: String,
    val description: String?,
    val engine: LlmModelEngine,
    val googleAiModel: GoogleAiModelsEnum? = null,
    val openAiModel: OpenAiModelsEnum? = null,
    val claudeModel: ClaudeModelsEnum? = null,
) {
    // Provider-agnostic rate limits: whichever provider enum is set supplies them.
    // Claude carries no limit data yet, so it stays null (= unconstrained).
    val limitRequestsInMinute: Int?
        get() = googleAiModel?.limitRequestsInMinute ?: openAiModel?.limitRequestsInMinute
    val limitRequestsInDay: Int?
        get() = googleAiModel?.limitRequestsInDay ?: openAiModel?.limitRequestsInDay
    val limitTokensInMinute: Int?
        get() = googleAiModel?.limitTokensInMinute ?: openAiModel?.limitTokensInMinute
    val limitTokensInDay: Int?
        get() = googleAiModel?.limitTokensInDay ?: openAiModel?.limitTokensInDay

    val title: String
        get() = googleAiModel?.title ?: openAiModel?.title ?: claudeModel?.title ?: id
    val shortTitle: String
        get() = googleAiModel?.shortTitle ?: openAiModel?.shortTitle ?: claudeModel?.shortTitle ?: id

    /** Takes a recorded phrase as input. */
    val hearsAudio: Boolean
        get() = googleAiModel?.hearsAudio ?: openAiModel?.hearsAudio ?: false

    /** Answers a text-only request. */
    val readsText: Boolean
        get() = openAiModel?.readsText ?: true

    /** The settings a request to this model may carry; a model outside the catalog takes none. */
    val parameterSupport: ModelParameterSupport
        get() = googleAiModel?.parameters ?: openAiModel?.parameters ?: claudeModel?.parameters ?: ModelParameterSupport()
}
