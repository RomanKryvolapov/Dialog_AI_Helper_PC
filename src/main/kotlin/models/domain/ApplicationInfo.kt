package models.domain

data class ApplicationInfo(
    val lastSelectedDevice: String,
    val selectedModel: LlmModel,
    val googleCloudToken: String,
    val prompt: String,
    val lastOpenedTab: Int,
    val promptsMap: Map<String, String>,
    val lmStudioConfig: LmStudioConfig,
    val ollamaConfig: OllamaConfig,
    val sendTextEverySymbols: Int,
    val sendTextEveryMilliseconds: Long,
    val voskModelPath: String,
)