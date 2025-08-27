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
                translateTextEverySymbols = translateTextEverySymbols
                    ?: defaultApplicationInfo.translateTextEverySymbols,
                translateTextEveryMilliseconds = translateTextEveryMilliseconds
                    ?: defaultApplicationInfo.translateTextEveryMilliseconds,
                voskModelPath = voskModelPath
                    ?: defaultApplicationInfo.voskModelPath
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
                translateTextEverySymbols = translateTextEverySymbols,
                translateTextEveryMilliseconds = translateTextEveryMilliseconds,
                voskModelPath = voskModelPath,
            )
        }
    }


}