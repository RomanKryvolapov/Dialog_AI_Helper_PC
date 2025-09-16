package mappers.domain

import defaultApplicationInfo
import mappers.base.BaseReverseMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable

/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
class ApplicationInfoMapper : BaseReverseMapper<ApplicationInfo, ApplicationInfoNullable>() {

    override fun reverse(model: ApplicationInfoNullable): ApplicationInfo {
        return with(model) {
            ApplicationInfo(
                lastSelectedDevice = lastSelectedDevice
                    ?: defaultApplicationInfo.lastSelectedDevice,
                selectedModel = selectedModel
                    ?: defaultApplicationInfo.selectedModel,
                googleCloudToken = googleCloudToken ?: defaultApplicationInfo.googleCloudToken,
                prompt = prompt ?: defaultApplicationInfo.prompt,
                lastOpenedTab = lastOpenedTab ?: defaultApplicationInfo.lastOpenedTab,
                lmStudioConfig = lmStudioConfig ?: defaultApplicationInfo.lmStudioConfig,
                promptsMap = promptsMap ?: defaultApplicationInfo.promptsMap,
                ollamaConfig = ollamaConfig ?: defaultApplicationInfo.ollamaConfig,
                sendTextEverySymbols = sendTextEverySymbols
                    ?: defaultApplicationInfo.sendTextEverySymbols,
                sendTextEveryMilliseconds = sendTextEveryMilliseconds
                    ?: defaultApplicationInfo.sendTextEveryMilliseconds,
                voskModelPath = voskModelPath
                    ?: defaultApplicationInfo.voskModelPath,
                whisperModelPath = whisperModelPath
                    ?: defaultApplicationInfo.whisperModelPath,
                whisperModelConfig = whisperModelConfig
                    ?: defaultApplicationInfo.whisperModelConfig,
                voiceRecognizer = voiceRecognizer
                    ?: defaultApplicationInfo.voiceRecognizer,
                voiceSpeaker = voiceSpeaker
                    ?: defaultApplicationInfo.voiceSpeaker,
                questionLanguage = questionLanguage
                    ?: defaultApplicationInfo.questionLanguage,
                answerLanguage = answerLanguage
                    ?: defaultApplicationInfo.answerLanguage,
            )
        }
    }

    override fun map(model: ApplicationInfo): ApplicationInfoNullable {
        return with(model) {
            ApplicationInfoNullable(
                lastSelectedDevice = lastSelectedDevice,
                selectedModel = selectedModel,
                googleCloudToken = googleCloudToken,
                prompt = prompt,
                lastOpenedTab = lastOpenedTab,
                ollamaConfig = ollamaConfig,
                promptsMap = promptsMap,
                lmStudioConfig = lmStudioConfig,
                sendTextEverySymbols = sendTextEverySymbols,
                sendTextEveryMilliseconds = sendTextEveryMilliseconds,
                voskModelPath = voskModelPath,
                whisperModelPath = whisperModelPath,
                whisperModelConfig = whisperModelConfig,
                voiceRecognizer = voiceRecognizer,
                voiceSpeaker = voiceSpeaker,
                questionLanguage = questionLanguage,
                answerLanguage = answerLanguage,
            )
        }
    }


}