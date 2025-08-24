package mappers.domain

import defaultApplicationInfo
import extensions.getEnumValue
import mappers.base.BaseReverseMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable
import models.common.ApplicationLanguage
import models.common.GoogleAiModelsEnum

/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
object ApplicationInfoMapper : BaseReverseMapper<ApplicationInfo, ApplicationInfoNullable>() {

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
                lmStudioModels = lmStudioModels ?: defaultApplicationInfo.lmStudioModels,
                promptsMap = promptsMap ?: defaultApplicationInfo.promptsMap,
                lmStudioPort = lmStudioPort ?: defaultApplicationInfo.lmStudioPort,
                translateTextEverySymbols = translateTextEverySymbols
                    ?: defaultApplicationInfo.translateTextEverySymbols,
                translateTextEveryMilliseconds = translateTextEveryMilliseconds
                    ?: defaultApplicationInfo.translateTextEveryMilliseconds,
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
                lmStudioModels = lmStudioModels,
                promptsMap = promptsMap,
                lmStudioPort = lmStudioPort,
                translateTextEverySymbols = translateTextEverySymbols,
                translateTextEveryMilliseconds = translateTextEveryMilliseconds,
            )
        }
    }


}