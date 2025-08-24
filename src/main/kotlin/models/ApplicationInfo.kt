package models

data class ApplicationInfo(
    val lastSelectedDevice: String,
    val selectedModel: TranslateWithGoogleAiModelsEnum,
    val selectedFromLanguage: ApplicationLanguage,
    val selectedToLanguage: ApplicationLanguage,
    val googleCloudToken: String,
    val prompt: String,
    val lastOpenedTab: Int,
    val lmStudioModelName: String,
    val promptsMap: Map<String, String>,
)