package tabs

import models.domain.ApplicationInfo
import repository.PreferencesRepository

abstract class BaseTab(
    private val preferencesRepository: PreferencesRepository
) {

    fun getAppInfo() = preferencesRepository.getAppInfo()

    fun saveAppInfo(appInfo: ApplicationInfo) {
        preferencesRepository.saveAppInfo(appInfo)
    }

}