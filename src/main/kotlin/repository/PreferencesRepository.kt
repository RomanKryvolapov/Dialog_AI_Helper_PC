package repository

import kotlinx.coroutines.flow.Flow
import models.domain.ApplicationInfo

interface PreferencesRepository {

    val appInfoFlow: Flow<ApplicationInfo>
    fun getAppInfo(): ApplicationInfo
    fun saveAppInfo(applicationInfo: ApplicationInfo)
    fun clear()

}