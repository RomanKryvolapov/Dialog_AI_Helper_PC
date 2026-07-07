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
}
