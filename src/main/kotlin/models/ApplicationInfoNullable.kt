package models

data class ApplicationInfoNullable(
    val lastSelectedDevice: String?,
    val selectedModel: String?,
    val selectedFromLanguage: String?,
    val selectedToLanguage: String?,
    val googleCloudToken: String?,
    val prompt: String?,
    val lastOpenedTab: Int?,
    val lmStudioModelName: String?,
    val promptsMap: Map<String, String>?,
)
