package models.domain

data class ApplicationInfo(
    val lastSelectedDevice: String,
    /**
     * Method 1 first, then its reserves in the order they are tried; never empty. Method 1 also decides
     * how the audio is taken in: cut into recorded phrases (a voice method, Whisper batch) or streamed to
     * its recogniser.
     */
    val translationMethods: List<TranslationMethod>,
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
    val useStreamingResponse: Boolean,
    val sherpaModelPath: String,
    val sherpaConfig: SherpaModelConfig,
) {
    val primaryMethod: TranslationMethod get() = translationMethods.first()
}
