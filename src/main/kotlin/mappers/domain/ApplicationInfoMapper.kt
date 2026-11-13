package mappers.domain

import defaultApplicationInfo
import mappers.base.BaseReverseMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable
import models.domain.PromptParts
import models.domain.PromptPartsNullable
import models.domain.PromptSettings
import models.domain.PromptSettingsNullable
import models.domain.SherpaModelConfig
import models.domain.SherpaModelConfigNullable
import models.domain.QuestionsSettings
import models.domain.QuestionsSettingsNullable
import models.domain.SpeechmaticsConfig
import models.domain.resolveLlmModel
import models.domain.SpeechmaticsConfigNullable
import models.domain.SummarySettings
import models.domain.SummarySettingsNullable
import models.domain.WhisperModelConfig
import models.domain.WhisperModelConfigNullable
import models.domain.onRecognizer
import models.domain.resolveMethodChain

class ApplicationInfoMapper : BaseReverseMapper<ApplicationInfo, ApplicationInfoNullable>() {

    override fun reverse(model: ApplicationInfoNullable): ApplicationInfo {
        return with(model) {
            ApplicationInfo(
                lastSelectedDevice = lastSelectedDevice ?: defaultApplicationInfo.lastSelectedDevice,
                translationMethods = resolveMethodChain(translationMethods.orEmpty(), legacyRecognizer = voiceRecognitionEngine)
                    .ifEmpty { onRecognizer(defaultApplicationInfo.translationMethods, voiceRecognitionEngine) },
                googleCloudToken = googleCloudToken ?: defaultApplicationInfo.googleCloudToken,
                openAiToken = openAiToken ?: defaultApplicationInfo.openAiToken,
                claudeToken = claudeToken ?: defaultApplicationInfo.claudeToken,
                speechmaticsToken = speechmaticsToken ?: defaultApplicationInfo.speechmaticsToken,
                prompts = reversePrompts(prompts),
                modelParameters = modelParameters.orEmpty(),
                lastOpenedTab = lastOpenedTab ?: defaultApplicationInfo.lastOpenedTab,
                whisperModelPath = whisperModelPath ?: defaultApplicationInfo.whisperModelPath,
                whisperModelConfig = reverseWhisperConfig(whisperModelConfig),
                questionLanguage = questionLanguage ?: defaultApplicationInfo.questionLanguage,
                useStreamingResponse = useStreamingResponse ?: defaultApplicationInfo.useStreamingResponse,
                sherpaModelPath = sherpaModelPath ?: defaultApplicationInfo.sherpaModelPath,
                sherpaConfig = reverseSherpaConfig(sherpaConfig),
                questions = defaultApplicationInfo.questions.let { default ->
                    QuestionsSettings(
                        enabled = questions?.enabled ?: default.enabled,
                        // A model the catalog dropped falls back to the built-in choice.
                        modelId = questions?.modelId?.let { id -> resolveLlmModel(id)?.takeIf { it.readsText }?.id } ?: default.modelId,
                        phrases = questions?.phrases ?: default.phrases,
                        fullAnswers = questions?.fullAnswers ?: default.fullAnswers,
                        system = questions?.system.orDefault(default.system),
                        user = questions?.user.orDefault(default.user),
                    )
                },
                speechmaticsConfig = SpeechmaticsConfig(
                    endpoint = speechmaticsConfig?.endpoint ?: defaultApplicationInfo.speechmaticsConfig.endpoint,
                    model = speechmaticsConfig?.model ?: defaultApplicationInfo.speechmaticsConfig.model,
                    speakerLabels = speechmaticsConfig?.speakerLabels ?: defaultApplicationInfo.speechmaticsConfig.speakerLabels,
                ),
            )
        }
    }

    override fun map(model: ApplicationInfo): ApplicationInfoNullable {
        return with(model) {
            ApplicationInfoNullable(
                lastSelectedDevice = lastSelectedDevice,
                translationMethods = translationMethods.map { it.key },
                googleCloudToken = googleCloudToken,
                openAiToken = openAiToken,
                claudeToken = claudeToken,
                speechmaticsToken = speechmaticsToken,
                prompts = mapPrompts(prompts),
                modelParameters = modelParameters,
                lastOpenedTab = lastOpenedTab,
                whisperModelPath = whisperModelPath,
                whisperModelConfig = mapWhisperConfig(whisperModelConfig),
                questionLanguage = questionLanguage,
                useStreamingResponse = useStreamingResponse,
                sherpaModelPath = sherpaModelPath,
                sherpaConfig = mapSherpaConfig(sherpaConfig),
                questions = QuestionsSettingsNullable(
                    enabled = questions.enabled,
                    modelId = questions.modelId,
                    phrases = questions.phrases,
                    fullAnswers = questions.fullAnswers,
                    system = questions.system,
                    user = questions.user,
                ),
                speechmaticsConfig = SpeechmaticsConfigNullable(
                    endpoint = speechmaticsConfig.endpoint,
                    model = speechmaticsConfig.model,
                    speakerLabels = speechmaticsConfig.speakerLabels,
                ),
            )
        }
    }

    /** A part left blank counts as never edited: the built-in text applies. */
    private fun reversePrompts(model: PromptSettingsNullable?): PromptSettings {
        val default = defaultApplicationInfo.prompts
        val summary = model?.summary
        return PromptSettings(
            previousPhrases = model?.previousPhrases ?: default.previousPhrases,
            text = reverseParts(model?.text, default.text),
            summary = SummarySettings(
                enabled = summary?.enabled ?: default.summary.enabled,
                everyPhrases = summary?.everyPhrases ?: default.summary.everyPhrases,
                system = summary?.system.orDefault(default.summary.system),
                user = summary?.user.orDefault(default.summary.user),
            ),
            voice = reverseParts(model?.voice, default.voice),
        )
    }

    private fun reverseParts(model: PromptPartsNullable?, default: PromptParts) = PromptParts(
        system = model?.system.orDefault(default.system),
        user = model?.user.orDefault(default.user),
        previousPhrase = model?.previousPhrase.orDefault(default.previousPhrase),
    )

    private fun String?.orDefault(default: String): String = this?.takeIf { it.isNotBlank() } ?: default

    private fun mapPrompts(model: PromptSettings) = PromptSettingsNullable(
        previousPhrases = model.previousPhrases,
        text = mapParts(model.text),
        summary = SummarySettingsNullable(
            enabled = model.summary.enabled,
            everyPhrases = model.summary.everyPhrases,
            system = model.summary.system,
            user = model.summary.user,
        ),
        voice = mapParts(model.voice),
    )

    private fun mapParts(model: PromptParts) = PromptPartsNullable(
        system = model.system,
        user = model.user,
        previousPhrase = model.previousPhrase,
    )

    private fun reverseWhisperConfig(model: WhisperModelConfigNullable?): WhisperModelConfig {
        val default = defaultApplicationInfo.whisperModelConfig
        return WhisperModelConfig(
            silenceThresholdPercents = model?.silenceThresholdPercents ?: default.silenceThresholdPercents,
            maxSilenceMilliseconds = model?.maxSilenceMilliseconds ?: default.maxSilenceMilliseconds,
            maxChunkDurationMilliseconds = model?.maxChunkDurationMilliseconds ?: default.maxChunkDurationMilliseconds,
            maxConcurrentProcesses = model?.maxConcurrentProcesses ?: default.maxConcurrentProcesses,
            streamingStepMilliseconds = model?.streamingStepMilliseconds ?: default.streamingStepMilliseconds,
            streamingMinChars = model?.streamingMinChars ?: default.streamingMinChars,
        )
    }

    private fun mapWhisperConfig(model: WhisperModelConfig): WhisperModelConfigNullable {
        return WhisperModelConfigNullable(
            silenceThresholdPercents = model.silenceThresholdPercents,
            maxSilenceMilliseconds = model.maxSilenceMilliseconds,
            maxChunkDurationMilliseconds = model.maxChunkDurationMilliseconds,
            maxConcurrentProcesses = model.maxConcurrentProcesses,
            streamingStepMilliseconds = model.streamingStepMilliseconds,
            streamingMinChars = model.streamingMinChars,
        )
    }

    private fun reverseSherpaConfig(model: SherpaModelConfigNullable?): SherpaModelConfig {
        val default = defaultApplicationInfo.sherpaConfig
        return SherpaModelConfig(
            numThreads = model?.numThreads ?: default.numThreads,
            decodingMethod = model?.decodingMethod ?: default.decodingMethod,
            maxActivePaths = model?.maxActivePaths ?: default.maxActivePaths,
            splitMaxWords = model?.splitMaxWords ?: default.splitMaxWords,
            maxHangMillis = model?.maxHangMillis ?: default.maxHangMillis,
        )
    }

    private fun mapSherpaConfig(model: SherpaModelConfig): SherpaModelConfigNullable {
        return SherpaModelConfigNullable(
            numThreads = model.numThreads,
            decodingMethod = model.decodingMethod,
            maxActivePaths = model.maxActivePaths,
            splitMaxWords = model.splitMaxWords,
            maxHangMillis = model.maxHangMillis,
        )
    }

}
