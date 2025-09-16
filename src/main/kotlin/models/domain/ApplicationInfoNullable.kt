package models.domain

data class ApplicationInfoNullable(
    val lastSelectedDevice: String?,
    val selectedModel: LlmModel?,
    val googleCloudToken: String?,
    val prompt: PromptModel?,
    val lastOpenedTab: Int?,
    val promptsMap: Map<String, PromptModel>?,
    val lmStudioConfig: LmStudioConfig?,
    val ollamaConfig: OllamaConfig?,
    val sendTextEverySymbols: Int?,
    val sendTextEveryMilliseconds: Long?,
    val voskModelPath: String?,
    val whisperModelPath: String?,
    val whisperModelConfig: WhisperModelConfig?,
    val voiceRecognizer: VoiceRecognizerEngineEnum?,
    val voiceSpeaker: VoiceSpeakerEngineEnum?,
    val questionLanguage: String?,
    val answerLanguage: String?,
)
