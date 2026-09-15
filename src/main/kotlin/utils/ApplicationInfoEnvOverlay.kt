package utils

import defaultApplicationInfo
import models.domain.ApplicationInfoNullable
import models.domain.LlmModel
import models.domain.PromptModel
import models.domain.SherpaModelConfig
import models.domain.SherpaModelConfigNullable
import models.domain.VoiceRecognitionEngine
import models.domain.WhisperModelConfigNullable
import models.domain.findLlmModelById
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps the `.env` keys onto the stored settings.
 *
 * [fillFromEnv] supplies a value for every setting the user has not filled in, so the order of
 * precedence is: what the user saved → what the environment provides → the built-in default.
 *
 * [dropFallbackValues] is the other half, and the feature does not work without it: only a value
 * that differs from what the fallbacks would produce anyway is written to the preferences. The app
 * saves the whole settings object on every change (and once on every startup), so without this
 * every untouched setting would be written out at its default value, stop counting as "not filled
 * in", and `.env` would go inert for it from the first launch onwards.
 *
 * The trade-off: typing a value that happens to equal the fallback is the same as not typing it.
 * The app behaves identically either way — until `.env` changes, which is the point of the feature.
 * The prompt library is the one field always written whole: it is the app's own data, not a setting.
 *
 * A saved value always beats `.env`, and there is no way to un-choose one from the Settings screen
 * except by clearing the field, so a `.env` key that loses that contest says so in the log.
 */
class ApplicationInfoEnvOverlay(
    private val envSettings: EnvSettings,
) {

    companion object {
        const val KEY_AUDIO_INPUT_DEVICE = "DIALOG_AUDIO_INPUT_DEVICE"
        const val KEY_QUESTION_LANGUAGE = "DIALOG_QUESTION_LANGUAGE"
        const val KEY_VOICE_RECOGNITION_ENGINE = "DIALOG_VOICE_RECOGNITION_ENGINE"

        const val KEY_LLM_MODEL_ID = "DIALOG_LLM_MODEL_ID"
        const val KEY_USE_STREAMING_RESPONSE = "DIALOG_USE_STREAMING_RESPONSE"
        const val KEY_GOOGLE_API_KEY = "DIALOG_GOOGLE_API_KEY"
        const val KEY_OPENAI_API_KEY = "DIALOG_OPENAI_API_KEY"
        const val KEY_CLAUDE_API_KEY = "DIALOG_CLAUDE_API_KEY"
        const val KEY_SPEECHMATICS_API_KEY = "DIALOG_SPEECHMATICS_API_KEY"

        const val KEY_PROMPT_NAME = "DIALOG_PROMPT_NAME"
        const val KEY_LAST_OPENED_TAB = "DIALOG_LAST_OPENED_TAB"
        const val KEY_SEND_TEXT_EVERY_SYMBOLS = "DIALOG_SEND_TEXT_EVERY_SYMBOLS"
        const val KEY_SEND_TEXT_EVERY_MILLISECONDS = "DIALOG_SEND_TEXT_EVERY_MILLISECONDS"

        const val KEY_WHISPER_MODEL_PATH = "DIALOG_WHISPER_MODEL_PATH"
        const val KEY_WHISPER_SILENCE_THRESHOLD_PERCENTS = "DIALOG_WHISPER_SILENCE_THRESHOLD_PERCENTS"
        const val KEY_WHISPER_MAX_SILENCE_MS = "DIALOG_WHISPER_MAX_SILENCE_MS"
        const val KEY_WHISPER_MAX_CHUNK_DURATION_MS = "DIALOG_WHISPER_MAX_CHUNK_DURATION_MS"
        const val KEY_WHISPER_MAX_CONCURRENT_PROCESSES = "DIALOG_WHISPER_MAX_CONCURRENT_PROCESSES"
        const val KEY_WHISPER_STREAMING_STEP_MS = "DIALOG_WHISPER_STREAMING_STEP_MS"
        const val KEY_WHISPER_STREAMING_MIN_CHARS = "DIALOG_WHISPER_STREAMING_MIN_CHARS"

        const val KEY_SHERPA_MODEL_PATH = "DIALOG_SHERPA_MODEL_PATH"
        const val KEY_SHERPA_NUM_THREADS = "DIALOG_SHERPA_NUM_THREADS"
        const val KEY_SHERPA_DECODING_METHOD = "DIALOG_SHERPA_DECODING_METHOD"
        const val KEY_SHERPA_MAX_ACTIVE_PATHS = "DIALOG_SHERPA_MAX_ACTIVE_PATHS"
        const val KEY_SHERPA_SPLIT_MAX_WORDS = "DIALOG_SHERPA_SPLIT_MAX_WORDS"
        const val KEY_SHERPA_MAX_HANG_MS = "DIALOG_SHERPA_MAX_HANG_MS"
    }

    private val log = LoggerFactory.getLogger("ApplicationInfoEnvOverlay")

    // The settings are re-read on every recognized phrase, so a bad value must not flood the log.
    private val warnedKeys = ConcurrentHashMap.newKeySet<String>()

    private val envLlmModel: LlmModel? by lazy {
        val id = envSettings.getString(KEY_LLM_MODEL_ID) ?: return@lazy null
        findLlmModelById(id) ?: run {
            warnOnce(KEY_LLM_MODEL_ID, "$KEY_LLM_MODEL_ID does not name a known model, ignored")
            null
        }
    }

    private val envDecodingMethod: String? by lazy {
        val method = envSettings.getString(KEY_SHERPA_DECODING_METHOD) ?: return@lazy null
        method.takeIf { it in SherpaModelConfig.DECODING_METHODS } ?: run {
            warnOnce(
                KEY_SHERPA_DECODING_METHOD,
                "$KEY_SHERPA_DECODING_METHOD must be one of ${SherpaModelConfig.DECODING_METHODS}, ignored",
            )
            null
        }
    }

    private val envVoiceRecognitionEngine: VoiceRecognitionEngine? by lazy {
        envSettings.getEnum(KEY_VOICE_RECOGNITION_ENGINE, VoiceRecognitionEngine.entries.toTypedArray())
    }

    fun fillFromEnv(stored: ApplicationInfoNullable): ApplicationInfoNullable {
        val whisper = stored.whisperModelConfig ?: WhisperModelConfigNullable()
        val sherpa = stored.sherpaConfig ?: SherpaModelConfigNullable()
        return stored.copy(
            lastSelectedDevice = stored.lastSelectedDevice.orEnv(KEY_AUDIO_INPUT_DEVICE, envAudioInputDevice()),
            questionLanguage = stored.questionLanguage.orEnv(KEY_QUESTION_LANGUAGE, envQuestionLanguage()),
            voiceRecognitionEngine = stored.voiceRecognitionEngine
                .orEnvValue(KEY_VOICE_RECOGNITION_ENGINE, envVoiceRecognitionEngine),

            selectedModel = envSelectedModel(stored.selectedModel),
            useStreamingResponse = stored.useStreamingResponse
                .orEnvValue(KEY_USE_STREAMING_RESPONSE, envUseStreamingResponse()),
            googleCloudToken = stored.googleCloudToken.orEnv(KEY_GOOGLE_API_KEY, envGoogleToken()),
            openAiToken = stored.openAiToken.orEnv(KEY_OPENAI_API_KEY, envOpenAiToken()),
            claudeToken = stored.claudeToken.orEnv(KEY_CLAUDE_API_KEY, envClaudeToken()),
            speechmaticsToken = stored.speechmaticsToken.orEnv(KEY_SPEECHMATICS_API_KEY, envSpeechmaticsToken()),

            prompt = stored.prompt.orEnvValue(KEY_PROMPT_NAME, envPrompt(stored.promptsMap)),
            lastOpenedTab = stored.lastOpenedTab.orEnvValue(KEY_LAST_OPENED_TAB, envLastOpenedTab()),
            sendTextEverySymbols = stored.sendTextEverySymbols
                .orEnvValue(KEY_SEND_TEXT_EVERY_SYMBOLS, envSendTextEverySymbols()),
            sendTextEveryMilliseconds = stored.sendTextEveryMilliseconds
                .orEnvValue(KEY_SEND_TEXT_EVERY_MILLISECONDS, envSendTextEveryMilliseconds()),

            whisperModelPath = stored.whisperModelPath.orEnv(KEY_WHISPER_MODEL_PATH, envWhisperModelPath()),
            whisperModelConfig = whisper.copy(
                silenceThresholdPercents = whisper.silenceThresholdPercents
                    .orEnvValue(KEY_WHISPER_SILENCE_THRESHOLD_PERCENTS, envSilenceThreshold()),
                maxSilenceMilliseconds = whisper.maxSilenceMilliseconds
                    .orEnvValue(KEY_WHISPER_MAX_SILENCE_MS, envMaxSilenceMs()),
                maxChunkDurationMilliseconds = whisper.maxChunkDurationMilliseconds
                    .orEnvValue(KEY_WHISPER_MAX_CHUNK_DURATION_MS, envMaxChunkDurationMs()),
                maxConcurrentProcesses = whisper.maxConcurrentProcesses
                    .orEnvValue(KEY_WHISPER_MAX_CONCURRENT_PROCESSES, envMaxConcurrentProcesses()),
                streamingStepMilliseconds = whisper.streamingStepMilliseconds
                    .orEnvValue(KEY_WHISPER_STREAMING_STEP_MS, envStreamingStepMs()),
                streamingMinChars = whisper.streamingMinChars
                    .orEnvValue(KEY_WHISPER_STREAMING_MIN_CHARS, envStreamingMinChars()),
            ),

            sherpaModelPath = stored.sherpaModelPath.orEnv(KEY_SHERPA_MODEL_PATH, envSherpaModelPath()),
            sherpaConfig = sherpa.copy(
                numThreads = sherpa.numThreads.orEnvValue(KEY_SHERPA_NUM_THREADS, envSherpaNumThreads()),
                decodingMethod = sherpa.decodingMethod.orEnv(KEY_SHERPA_DECODING_METHOD, envDecodingMethod),
                maxActivePaths = sherpa.maxActivePaths
                    .orEnvValue(KEY_SHERPA_MAX_ACTIVE_PATHS, envSherpaMaxActivePaths()),
                splitMaxWords = sherpa.splitMaxWords
                    .orEnvValue(KEY_SHERPA_SPLIT_MAX_WORDS, envSherpaSplitMaxWords()),
                maxHangMillis = sherpa.maxHangMillis.orEnvValue(KEY_SHERPA_MAX_HANG_MS, envSherpaMaxHangMs()),
            ),
        )
    }

    /**
     * Clears every field whose value the fallbacks would produce on their own, so that only what
     * the user actually chose is stored. See the class comment for why this is not optional.
     */
    fun dropFallbackValues(toSave: ApplicationInfoNullable): ApplicationInfoNullable {
        val whisper = toSave.whisperModelConfig
        val sherpa = toSave.sherpaConfig
        val defaultWhisper = defaultApplicationInfo.whisperModelConfig
        val defaultSherpa = defaultApplicationInfo.sherpaConfig
        return toSave.copy(
            lastSelectedDevice = toSave.lastSelectedDevice
                .dropIfFallback(envAudioInputDevice() ?: defaultApplicationInfo.lastSelectedDevice),
            questionLanguage = toSave.questionLanguage
                .dropIfFallback(envQuestionLanguage() ?: defaultApplicationInfo.questionLanguage),
            voiceRecognitionEngine = toSave.voiceRecognitionEngine
                .dropIfFallback(envVoiceRecognitionEngine ?: defaultApplicationInfo.voiceRecognitionEngine),

            selectedModel = toSave.selectedModel
                ?.takeUnless { it.id == (envLlmModel ?: defaultApplicationInfo.selectedModel).id },
            useStreamingResponse = toSave.useStreamingResponse
                .dropIfFallback(envUseStreamingResponse() ?: defaultApplicationInfo.useStreamingResponse),
            googleCloudToken = toSave.googleCloudToken
                .dropIfFallback(envGoogleToken() ?: defaultApplicationInfo.googleCloudToken),
            openAiToken = toSave.openAiToken
                .dropIfFallback(envOpenAiToken() ?: defaultApplicationInfo.openAiToken),
            claudeToken = toSave.claudeToken
                .dropIfFallback(envClaudeToken() ?: defaultApplicationInfo.claudeToken),
            speechmaticsToken = toSave.speechmaticsToken
                .dropIfFallback(envSpeechmaticsToken() ?: defaultApplicationInfo.speechmaticsToken),

            // Compared whole, not by name: an edited prompt keeps the name of the one it came from.
            prompt = toSave.prompt
                .dropIfFallback(envPrompt(toSave.promptsMap) ?: defaultApplicationInfo.prompt),
            lastOpenedTab = toSave.lastOpenedTab
                .dropIfFallback(envLastOpenedTab() ?: defaultApplicationInfo.lastOpenedTab),
            sendTextEverySymbols = toSave.sendTextEverySymbols
                .dropIfFallback(envSendTextEverySymbols() ?: defaultApplicationInfo.sendTextEverySymbols),
            sendTextEveryMilliseconds = toSave.sendTextEveryMilliseconds
                .dropIfFallback(envSendTextEveryMilliseconds() ?: defaultApplicationInfo.sendTextEveryMilliseconds),

            whisperModelPath = toSave.whisperModelPath
                .dropIfFallback(envWhisperModelPath() ?: defaultApplicationInfo.whisperModelPath),
            whisperModelConfig = whisper?.copy(
                silenceThresholdPercents = whisper.silenceThresholdPercents
                    .dropIfFallback(envSilenceThreshold() ?: defaultWhisper.silenceThresholdPercents),
                maxSilenceMilliseconds = whisper.maxSilenceMilliseconds
                    .dropIfFallback(envMaxSilenceMs() ?: defaultWhisper.maxSilenceMilliseconds),
                maxChunkDurationMilliseconds = whisper.maxChunkDurationMilliseconds
                    .dropIfFallback(envMaxChunkDurationMs() ?: defaultWhisper.maxChunkDurationMilliseconds),
                maxConcurrentProcesses = whisper.maxConcurrentProcesses
                    .dropIfFallback(envMaxConcurrentProcesses() ?: defaultWhisper.maxConcurrentProcesses),
                streamingStepMilliseconds = whisper.streamingStepMilliseconds
                    .dropIfFallback(envStreamingStepMs() ?: defaultWhisper.streamingStepMilliseconds),
                streamingMinChars = whisper.streamingMinChars
                    .dropIfFallback(envStreamingMinChars() ?: defaultWhisper.streamingMinChars),
            ),

            sherpaModelPath = toSave.sherpaModelPath
                .dropIfFallback(envSherpaModelPath() ?: defaultApplicationInfo.sherpaModelPath),
            sherpaConfig = sherpa?.copy(
                numThreads = sherpa.numThreads
                    .dropIfFallback(envSherpaNumThreads() ?: defaultSherpa.numThreads),
                decodingMethod = sherpa.decodingMethod
                    .dropIfFallback(envDecodingMethod ?: defaultSherpa.decodingMethod),
                maxActivePaths = sherpa.maxActivePaths
                    .dropIfFallback(envSherpaMaxActivePaths() ?: defaultSherpa.maxActivePaths),
                splitMaxWords = sherpa.splitMaxWords
                    .dropIfFallback(envSherpaSplitMaxWords() ?: defaultSherpa.splitMaxWords),
                maxHangMillis = sherpa.maxHangMillis
                    .dropIfFallback(envSherpaMaxHangMs() ?: defaultSherpa.maxHangMillis),
            ),
        )
    }

    private fun envAudioInputDevice() = envSettings.getString(KEY_AUDIO_INPUT_DEVICE)
    private fun envQuestionLanguage() = envSettings.getString(KEY_QUESTION_LANGUAGE)
    private fun envUseStreamingResponse() = envSettings.getBoolean(KEY_USE_STREAMING_RESPONSE)
    private fun envGoogleToken() = envSettings.getString(KEY_GOOGLE_API_KEY)
    private fun envOpenAiToken() = envSettings.getString(KEY_OPENAI_API_KEY)
    private fun envClaudeToken() = envSettings.getString(KEY_CLAUDE_API_KEY)
    private fun envSpeechmaticsToken() = envSettings.getString(KEY_SPEECHMATICS_API_KEY)
    private fun envLastOpenedTab() = envSettings.getInt(KEY_LAST_OPENED_TAB, min = 0)
    private fun envSendTextEverySymbols() = envSettings.getInt(KEY_SEND_TEXT_EVERY_SYMBOLS, min = 1)
    private fun envSendTextEveryMilliseconds() = envSettings.getLong(KEY_SEND_TEXT_EVERY_MILLISECONDS, min = 1)
    private fun envWhisperModelPath() = envSettings.getString(KEY_WHISPER_MODEL_PATH)
    private fun envSherpaModelPath() = envSettings.getString(KEY_SHERPA_MODEL_PATH)

    // The lower bounds below are the ones the Settings screen enforces on the same fields.
    private fun envSilenceThreshold() = envSettings.getInt(KEY_WHISPER_SILENCE_THRESHOLD_PERCENTS, min = 0)
    private fun envMaxSilenceMs() = envSettings.getLong(KEY_WHISPER_MAX_SILENCE_MS, min = 1)
    private fun envMaxChunkDurationMs() = envSettings.getLong(KEY_WHISPER_MAX_CHUNK_DURATION_MS, min = 1)
    private fun envMaxConcurrentProcesses() = envSettings.getInt(KEY_WHISPER_MAX_CONCURRENT_PROCESSES, min = 1)
    private fun envStreamingStepMs() = envSettings.getLong(KEY_WHISPER_STREAMING_STEP_MS, min = 100)
    private fun envStreamingMinChars() = envSettings.getInt(KEY_WHISPER_STREAMING_MIN_CHARS, min = 1)
    private fun envSherpaNumThreads() = envSettings.getInt(KEY_SHERPA_NUM_THREADS, min = 1)
    private fun envSherpaMaxActivePaths() = envSettings.getInt(KEY_SHERPA_MAX_ACTIVE_PATHS, min = 1)
    private fun envSherpaSplitMaxWords() = envSettings.getInt(KEY_SHERPA_SPLIT_MAX_WORDS, min = 0)
    private fun envSherpaMaxHangMs() = envSettings.getLong(KEY_SHERPA_MAX_HANG_MS, min = 0)

    private fun envPrompt(storedPrompts: Map<String, PromptModel>?): PromptModel? {
        val name = envSettings.getString(KEY_PROMPT_NAME) ?: return null
        val prompt = (storedPrompts ?: defaultApplicationInfo.promptsMap)[name]
        if (prompt == null) warnOnce(KEY_PROMPT_NAME, "$KEY_PROMPT_NAME does not name a known prompt, ignored")
        return prompt
    }

    private fun warnOnce(key: String, message: String) {
        if (warnedKeys.add(key)) log.warn(message)
    }

    private fun envSelectedModel(stored: LlmModel?): LlmModel? {
        if (stored == null) return envLlmModel
        if (envLlmModel != null && envLlmModel?.id != stored.id) {
            envSettings.noteOverriddenByStoredValue(KEY_LLM_MODEL_ID)
        }
        return stored
    }

    /** A blank saved string counts as "not filled in", so a fallback supplies it. */
    private fun String?.orEnv(key: String, fromEnv: String?): String? {
        return this?.takeIf { it.isNotBlank() }.orEnvValue(key, fromEnv)
    }

    /**
     * The saved value wins, but a `.env` key that loses this way is announced: a key that quietly
     * does nothing is the hardest thing to diagnose about a fallback file.
     */
    private fun <T : Any> T?.orEnvValue(key: String, fromEnv: T?): T? {
        if (this == null) return fromEnv
        if (fromEnv != null && this != fromEnv) envSettings.noteOverriddenByStoredValue(key)
        return this
    }

    private fun <T : Any> T?.dropIfFallback(fallback: T): T? {
        return this?.takeUnless { it == fallback }
    }

    /** A field the user emptied is stored as absent, which is what the reader treats it as. */
    private fun String?.dropIfFallback(fallback: String): String? {
        return this?.takeIf { it.isNotBlank() }?.takeUnless { it == fallback }
    }

}
