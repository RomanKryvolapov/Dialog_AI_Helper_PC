package mappers.domain

import defaultApplicationInfo
import mappers.base.BaseReverseMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable

class ApplicationInfoMapper : BaseReverseMapper<ApplicationInfo, ApplicationInfoNullable>() {

    override fun reverse(model: ApplicationInfoNullable): ApplicationInfo {
        return with(model) {
            ApplicationInfo(
                lastSelectedDevice = lastSelectedDevice ?: defaultApplicationInfo.lastSelectedDevice,
                selectedModel = selectedModel ?: defaultApplicationInfo.selectedModel,
                googleCloudToken = googleCloudToken ?: defaultApplicationInfo.googleCloudToken,
                openAiToken = openAiToken ?: defaultApplicationInfo.openAiToken,
                claudeToken = claudeToken ?: defaultApplicationInfo.claudeToken,
                prompt = prompt ?: defaultApplicationInfo.prompt,
                lastOpenedTab = lastOpenedTab ?: defaultApplicationInfo.lastOpenedTab,
                promptsMap = promptsMap ?: defaultApplicationInfo.promptsMap,
                sendTextEverySymbols = sendTextEverySymbols ?: defaultApplicationInfo.sendTextEverySymbols,
                sendTextEveryMilliseconds = sendTextEveryMilliseconds ?: defaultApplicationInfo.sendTextEveryMilliseconds,
                whisperModelPath = whisperModelPath ?: defaultApplicationInfo.whisperModelPath,
                whisperModelConfig = whisperModelConfig ?: defaultApplicationInfo.whisperModelConfig,
                questionLanguage = questionLanguage ?: defaultApplicationInfo.questionLanguage,
                voiceRecognitionEngine = voiceRecognitionEngine ?: defaultApplicationInfo.voiceRecognitionEngine,
                useStreamingResponse = useStreamingResponse ?: defaultApplicationInfo.useStreamingResponse,
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
                prompt = prompt,
                lastOpenedTab = lastOpenedTab,
                promptsMap = promptsMap,
                sendTextEverySymbols = sendTextEverySymbols,
                sendTextEveryMilliseconds = sendTextEveryMilliseconds,
                whisperModelPath = whisperModelPath,
                whisperModelConfig = whisperModelConfig,
                questionLanguage = questionLanguage,
                voiceRecognitionEngine = voiceRecognitionEngine,
                useStreamingResponse = useStreamingResponse,
            )
        }
    }

}
