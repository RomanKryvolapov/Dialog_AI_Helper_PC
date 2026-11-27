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
import utils.PersonalTexts
import models.domain.SpeechmaticsConfig
import models.domain.resolveLlmModel
import models.domain.SpeechmaticsConfigNullable
import models.domain.SummarySettings
import models.domain.SummarySettingsNullable
import models.domain.WhisperModelConfig
import models.domain.WhisperModelConfigNullable
import models.domain.FloatingPanelMode
import models.domain.HotkeyAction
import models.domain.WindowSettings
import models.domain.WindowSettingsNullable
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
                // What `personal/` holds stands in for an untouched setting, as `.env` does for the others.
                questions = PersonalTexts.questionsDefaults.let { default ->
                    QuestionsSettings(
                        enabled = questions?.enabled ?: default.enabled,
                        // A model the catalog dropped falls back to the built-in choice.
                        modelId = questions?.modelId?.let { id -> resolveLlmModel(id)?.takeIf { it.readsText }?.id } ?: default.modelId,
                        phrases = questions?.phrases ?: default.phrases,
                        fullAnswers = questions?.fullAnswers ?: default.fullAnswers,
                        questionDisplayLanguage = questions?.questionDisplayLanguage ?: default.questionDisplayLanguage,
                        answerDisplayLanguage = questions?.answerDisplayLanguage ?: default.answerDisplayLanguage,
                        aboutUser = questions?.aboutUser ?: default.aboutUser,
                        topic = questions?.topic ?: default.topic,
                        expansionModelId = questions?.expansionModelId?.let { id -> resolveLlmModel(id)?.takeIf { it.readsText }?.id }
                            ?: default.expansionModelId,
                        system = questions?.system.orDefault(default.system),
                        user = questions?.user.orDefault(default.user),
                        followUpsSystem = questions?.followUpsSystem.orDefault(default.followUpsSystem),
                        followUpsUser = questions?.followUpsUser.orDefault(default.followUpsUser),
                        expansionSystem = questions?.expansionSystem.orDefault(default.expansionSystem),
                        expansionUser = questions?.expansionUser.orDefault(default.expansionUser),
                    )
                },
                speechmaticsConfig = SpeechmaticsConfig(
                    endpoint = speechmaticsConfig?.endpoint ?: defaultApplicationInfo.speechmaticsConfig.endpoint,
                    model = speechmaticsConfig?.model ?: defaultApplicationInfo.speechmaticsConfig.model,
                    speakerLabels = speechmaticsConfig?.speakerLabels ?: defaultApplicationInfo.speechmaticsConfig.speakerLabels,
                ),
                translationEnabled = translationEnabled ?: defaultApplicationInfo.translationEnabled,
                // A saved value the screen does not offer falls back to the built-in size.
                uiScalePercent = uiScalePercent?.takeIf { it in ApplicationInfo.UI_SCALE_PERCENTS }
                    ?: defaultApplicationInfo.uiScalePercent,
                window = defaultApplicationInfo.window.let { default ->
                    WindowSettings(
                        minimizeToTray = window?.minimizeToTray ?: default.minimizeToTray,
                        closeToTray = window?.closeToTray ?: default.closeToTray,
                        hideFromTaskbar = window?.hideFromTaskbar ?: default.hideFromTaskbar,
                        hotkeys = default.hotkeys + HotkeyAction.entries.mapNotNull { action ->
                            window?.hotkeys?.get(action.name)?.let { action to it }
                        },
                        floatingPanel = FloatingPanelMode.entries.firstOrNull { it.name == window?.floatingPanel }
                            ?: default.floatingPanel,
                        panelOpacityPercent = window?.panelOpacityPercent
                            ?.takeIf { it in WindowSettings.PANEL_OPACITY_PERCENTS } ?: default.panelOpacityPercent,
                        newAnswerSignal = window?.newAnswerSignal ?: default.newAnswerSignal,
                    )
                },
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
                    questionDisplayLanguage = questions.questionDisplayLanguage,
                    answerDisplayLanguage = questions.answerDisplayLanguage,
                    aboutUser = questions.aboutUser,
                    topic = questions.topic,
                    expansionModelId = questions.expansionModelId,
                    system = questions.system,
                    user = questions.user,
                    followUpsSystem = questions.followUpsSystem,
                    followUpsUser = questions.followUpsUser,
                    expansionSystem = questions.expansionSystem,
                    expansionUser = questions.expansionUser,
                ),
                speechmaticsConfig = SpeechmaticsConfigNullable(
                    endpoint = speechmaticsConfig.endpoint,
                    model = speechmaticsConfig.model,
                    speakerLabels = speechmaticsConfig.speakerLabels,
                ),
                translationEnabled = translationEnabled,
                uiScalePercent = uiScalePercent,
                window = WindowSettingsNullable(
                    minimizeToTray = window.minimizeToTray,
                    closeToTray = window.closeToTray,
                    hideFromTaskbar = window.hideFromTaskbar,
                    hotkeys = window.hotkeys.mapKeys { it.key.name },
                    floatingPanel = window.floatingPanel.name,
                    panelOpacityPercent = window.panelOpacityPercent,
                    newAnswerSignal = window.newAnswerSignal,
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
