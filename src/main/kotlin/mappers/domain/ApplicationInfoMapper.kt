package mappers.domain

import defaultApplicationInfo
import mappers.base.BaseReverseMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable
import models.domain.SherpaModelConfig
import models.domain.SherpaModelConfigNullable
import models.domain.WhisperModelConfig
import models.domain.WhisperModelConfigNullable

class ApplicationInfoMapper : BaseReverseMapper<ApplicationInfo, ApplicationInfoNullable>() {

    override fun reverse(model: ApplicationInfoNullable): ApplicationInfo {
        return with(model) {
            ApplicationInfo(
                lastSelectedDevice = lastSelectedDevice ?: defaultApplicationInfo.lastSelectedDevice,
                selectedModel = selectedModel ?: defaultApplicationInfo.selectedModel,
                googleCloudToken = googleCloudToken ?: defaultApplicationInfo.googleCloudToken,
                openAiToken = openAiToken ?: defaultApplicationInfo.openAiToken,
                claudeToken = claudeToken ?: defaultApplicationInfo.claudeToken,
                speechmaticsToken = speechmaticsToken ?: defaultApplicationInfo.speechmaticsToken,
                prompt = prompt ?: defaultApplicationInfo.prompt,
                lastOpenedTab = lastOpenedTab ?: defaultApplicationInfo.lastOpenedTab,
                promptsMap = promptsMap ?: defaultApplicationInfo.promptsMap,
                sendTextEverySymbols = sendTextEverySymbols ?: defaultApplicationInfo.sendTextEverySymbols,
                sendTextEveryMilliseconds = sendTextEveryMilliseconds ?: defaultApplicationInfo.sendTextEveryMilliseconds,
                whisperModelPath = whisperModelPath ?: defaultApplicationInfo.whisperModelPath,
                whisperModelConfig = reverseWhisperConfig(whisperModelConfig),
                questionLanguage = questionLanguage ?: defaultApplicationInfo.questionLanguage,
                voiceRecognitionEngine = voiceRecognitionEngine ?: defaultApplicationInfo.voiceRecognitionEngine,
                useStreamingResponse = useStreamingResponse ?: defaultApplicationInfo.useStreamingResponse,
                sherpaModelPath = sherpaModelPath ?: defaultApplicationInfo.sherpaModelPath,
                sherpaConfig = reverseSherpaConfig(sherpaConfig),
            )
        }
    }

    override fun map(model: ApplicationInfo): ApplicationInfoNullable {
        return with(model) {
            ApplicationInfoNullable(
                lastSelectedDevice = lastSelectedDevice,
                selectedModel = selectedModel,
                googleCloudToken = googleCloudToken,
                openAiToken = openAiToken,
                claudeToken = claudeToken,
                speechmaticsToken = speechmaticsToken,
                prompt = prompt,
                lastOpenedTab = lastOpenedTab,
                promptsMap = promptsMap,
                sendTextEverySymbols = sendTextEverySymbols,
                sendTextEveryMilliseconds = sendTextEveryMilliseconds,
                whisperModelPath = whisperModelPath,
                whisperModelConfig = mapWhisperConfig(whisperModelConfig),
                questionLanguage = questionLanguage,
                voiceRecognitionEngine = voiceRecognitionEngine,
                useStreamingResponse = useStreamingResponse,
                sherpaModelPath = sherpaModelPath,
                sherpaConfig = mapSherpaConfig(sherpaConfig),
            )
        }
    }

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
