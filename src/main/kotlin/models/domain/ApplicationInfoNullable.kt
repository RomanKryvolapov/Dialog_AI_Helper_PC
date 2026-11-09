package models.domain

data class ApplicationInfoNullable(
    val lastSelectedDevice: String? = null,
    /** Translation method keys ([TranslationMethod.key]), the primary first. */
    val translationMethods: List<String>? = null,
    /**
     * The single model saved before translation methods existed. Only read, to carry that choice over as
     * the primary text method; never written again.
     */
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
    /**
     * The recogniser saved when it was a setting of its own; it is part of each text method now. Only read,
     * to carry it into a chain saved before that (keys "text:<model id>"); never written again.
     */
    val voiceRecognitionEngine: VoiceRecognitionEngine? = null,
    val useStreamingResponse: Boolean? = null,
    val sherpaModelPath: String? = null,
    val sherpaConfig: SherpaModelConfigNullable? = null,
)
