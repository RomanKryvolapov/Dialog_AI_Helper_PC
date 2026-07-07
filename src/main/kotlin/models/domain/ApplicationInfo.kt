package models.domain

data class ApplicationInfo(
    val lastSelectedDevice: String,
    val selectedModel: LlmModel,
    val googleCloudToken: String,
    val openAiToken: String,
    val claudeToken: String,
    val speechmaticsToken: String,
    val prompt: PromptModel,
    val lastOpenedTab: Int,
    val promptsMap: Map<String, PromptModel>,
    val sendTextEverySymbols: Int,
    val sendTextEveryMilliseconds: Long,
    val whisperModelPath: String,
    val whisperModelConfig: WhisperModelConfig,
    val questionLanguage: String,
    val voiceRecognitionEngine: VoiceRecognitionEngine,
    val useStreamingResponse: Boolean,
    val sherpaModelPath: String,
    val sherpaConfig: SherpaModelConfig,
)
