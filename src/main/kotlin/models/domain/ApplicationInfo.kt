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
    /** Everything the translation requests are made of (Settings → Main prompt, Voice prompt). */
    val prompts: PromptSettings,
    /** The request settings the user chose per model id (Settings → LLM); a model not here takes its defaults. */
    val modelParameters: Map<String, ModelParameters>,
    val lastOpenedTab: Int,
    val whisperModelPath: String,
    val whisperModelConfig: WhisperModelConfig,
    val questionLanguage: String,
    val useStreamingResponse: Boolean,
    val sherpaModelPath: String,
    val sherpaConfig: SherpaModelConfig,
    val speechmaticsConfig: SpeechmaticsConfig,
    /** The Questions mode (Settings → Questions, the box in the top bar). */
    val questions: QuestionsSettings,
) {
    val primaryMethod: TranslationMethod get() = translationMethods.first()
}
