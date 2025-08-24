package repository

import applicationInfoAdapter
import defaultApplicationInfo
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfo
import prefs

object PreferencesRepository {

    const val APPLICATION_INFO_KEY = "APPLICATION_INFO_KEY"

    fun getAppInfo(): ApplicationInfo {
        try {
            val json = prefs.get(APPLICATION_INFO_KEY, "")
            val applicationInfoNullable = applicationInfoAdapter.fromJson(json)
            if (applicationInfoNullable == null) {
                return defaultApplicationInfo
            }
            return ApplicationInfoMapper.reverse(applicationInfoNullable)
        } catch (e: Exception) {
            e.printStackTrace()
            return defaultApplicationInfo
        }
    }

    fun saveAppInfo(applicationInfo: ApplicationInfo) {
        val applicationInfoNullable = ApplicationInfoMapper.map(applicationInfo)
        val json = applicationInfoAdapter.toJson(applicationInfoNullable)
        prefs.put(APPLICATION_INFO_KEY, json)
    }

}
