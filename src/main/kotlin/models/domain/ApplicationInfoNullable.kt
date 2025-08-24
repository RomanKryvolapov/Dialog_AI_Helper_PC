package models.domain

data class ApplicationInfoNullable(
    val lastSelectedDevice: String?,
    val selectedModel: LlmModel?,
    val selectedFromLanguage: ApplicationLanguage?,
    val selectedToLanguage: ApplicationLanguage?,
    val googleCloudToken: String?,
    val prompt: String?,
    val lastOpenedTab: Int?,
    val promptsMap: Map<String, String>?,
    val lmStudioConfig: LmStudioConfig?,
    val ollamaConfig: OllamaConfig?,
    val translateTextEverySymbols: Int?,
    val translateTextEveryMilliseconds: Long?,
    val voskModel: VoskModels?,
)
