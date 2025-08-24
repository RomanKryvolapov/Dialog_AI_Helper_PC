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
                selectedFromLanguage = selectedFromLanguage
                    ?: defaultApplicationInfo.selectedFromLanguage,
                selectedToLanguage = selectedToLanguage
                    ?: defaultApplicationInfo.selectedToLanguage,
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
                voskModel = voskModel
                    ?: defaultApplicationInfo.voskModel
            )
        }
    }

    override fun map(model: ApplicationInfo): ApplicationInfoNullable {
        return with(model) {
            ApplicationInfoNullable(
                lastSelectedDevice = lastSelectedDevice,
                selectedModel = selectedModel,
                selectedFromLanguage = selectedFromLanguage,
                selectedToLanguage = selectedToLanguage,
                googleCloudToken = googleCloudToken,
                prompt = prompt,
                lastOpenedTab = lastOpenedTab,
                ollamaConfig = ollamaConfig,
                promptsMap = promptsMap,
                lmStudioConfig = lmStudioConfig,
                translateTextEverySymbols = translateTextEverySymbols,
                translateTextEveryMilliseconds = translateTextEveryMilliseconds,
                voskModel = voskModel,
            )
        }
    }


}