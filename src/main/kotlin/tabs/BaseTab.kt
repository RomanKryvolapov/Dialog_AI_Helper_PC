package tabs

import models.domain.ApplicationInfo
import repository.PreferencesRepository

abstract class BaseTab {

    fun getAppInfo() = PreferencesRepository.getAppInfo()

    fun saveAppInfo(appInfo: ApplicationInfo) {
        PreferencesRepository.saveAppInfo(appInfo)
    }

}