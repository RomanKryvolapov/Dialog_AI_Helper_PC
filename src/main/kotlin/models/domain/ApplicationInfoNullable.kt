package models.domain

/**
 * The stored shape of [ApplicationInfo]: every field is optional, so "the user never set this"
 * is distinguishable from "the user set this". A field left null falls back to the `.env` file
 * and then to the built-in default.
 */
data class ApplicationInfoNullable(
    val lastSelectedDevice: String? = null,
    val selectedModel: LlmModel? = null,
    val googleCloudToken: String? = null,
    val openAiToken: String? = null,
    val claudeToken: String? = null,
    val speechmaticsToken: String? = null,
    val prompt: PromptModel? = null,
    val lastOpenedTab: Int? = null,
    val promptsMap: Map<String, PromptModel>? = null,
    val sendTextEverySymbols: Int? = null,
    val sendTextEveryMilliseconds: Long? = null,
    val whisperModelPath: String? = null,
    val whisperModelConfig: WhisperModelConfigNullable? = null,
    val questionLanguage: String? = null,
    val voiceRecognitionEngine: VoiceRecognitionEngine? = null,
    val useStreamingResponse: Boolean? = null,
    val sherpaModelPath: String? = null,
    val sherpaConfig: SherpaModelConfigNullable? = null,
)
