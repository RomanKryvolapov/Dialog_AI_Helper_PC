package repository

import models.domain.ApplicationInfo

interface PreferencesRepository {

    fun getAppInfo(): ApplicationInfo

    fun saveAppInfo(applicationInfo: ApplicationInfo)

    fun clear()

}