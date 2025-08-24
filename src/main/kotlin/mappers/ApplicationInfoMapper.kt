/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package mappers

import defaultApplicationInfo
import mappers.base.BaseReverseMapper
import models.ApplicationInfo
import models.ApplicationInfoNullable
import models.ApplicationLanguage
import models.TranslateWithGoogleAiModelsEnum
import utils.getEnumValue

object ApplicationInfoMapper : BaseReverseMapper<ApplicationInfo, ApplicationInfoNullable>() {

    override fun reverse(model: ApplicationInfoNullable): ApplicationInfo {
        return with(model) {
            ApplicationInfo(
                lastSelectedDevice = lastSelectedDevice
                    ?: defaultApplicationInfo.lastSelectedDevice,
                selectedModel = selectedModel?.let {
                    getEnumValue<TranslateWithGoogleAiModelsEnum>(it) ?: TranslateWithGoogleAiModelsEnum.GEMMA_3_27B
                } ?: defaultApplicationInfo.selectedModel,
                selectedFromLanguage = selectedFromLanguage?.let {
                    getEnumValue<ApplicationLanguage>(it) ?: ApplicationLanguage.ENGLISH
                } ?: defaultApplicationInfo.selectedFromLanguage,
                selectedToLanguage = selectedToLanguage?.let {
                    getEnumValue<ApplicationLanguage>(it) ?: ApplicationLanguage.ENGLISH
                } ?: defaultApplicationInfo.selectedToLanguage,
                googleCloudToken = googleCloudToken ?: defaultApplicationInfo.googleCloudToken,
                prompt = prompt ?: defaultApplicationInfo.prompt,
                lastOpenedTab = lastOpenedTab ?: defaultApplicationInfo.lastOpenedTab,
                lmStudioModelName = lmStudioModelName ?: defaultApplicationInfo.lmStudioModelName,
                promptsMap = promptsMap ?: defaultApplicationInfo.promptsMap,
            )
        }
    }

    override fun map(model: ApplicationInfo): ApplicationInfoNullable {
        return with(model) {
            ApplicationInfoNullable(
                lastSelectedDevice = lastSelectedDevice,
                selectedModel = selectedModel.type,
                selectedFromLanguage = selectedFromLanguage.type,
                selectedToLanguage = selectedToLanguage.type,
                googleCloudToken = googleCloudToken,
                prompt = prompt,
                lastOpenedTab = lastOpenedTab,
                lmStudioModelName = lmStudioModelName,
                promptsMap = promptsMap,
            )
        }
    }


}