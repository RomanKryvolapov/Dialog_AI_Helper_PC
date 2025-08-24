package repository

import com.squareup.moshi.JsonAdapter
import defaultApplicationInfo
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable
import java.util.prefs.Preferences

class PreferencesRepositoryImpl(
    private val prefs: Preferences,
    private val applicationInfoMapper: ApplicationInfoMapper,
    private val applicationInfoAdapter: JsonAdapter<ApplicationInfoNullable>,
) : PreferencesRepository {

    companion object {
        const val APPLICATION_INFO_KEY = "APPLICATION_INFO_KEY"
    }

    override fun getAppInfo(): ApplicationInfo {
        try {
            val json = prefs.get(APPLICATION_INFO_KEY, "")
            val applicationInfoNullable = applicationInfoAdapter.fromJson(json)
            if (applicationInfoNullable == null) {
                return defaultApplicationInfo
            }
            return applicationInfoMapper.reverse(applicationInfoNullable)
        } catch (e: Exception) {
            e.printStackTrace()
            return defaultApplicationInfo
        }
    }

    override fun saveAppInfo(applicationInfo: ApplicationInfo) {
        val applicationInfoNullable = applicationInfoMapper.map(applicationInfo)
        val json = applicationInfoAdapter.toJson(applicationInfoNullable)
        prefs.put(APPLICATION_INFO_KEY, json)
    }

    override fun clear() {
        val applicationInfoNullable = applicationInfoMapper.map(defaultApplicationInfo)
        val json = applicationInfoAdapter.toJson(applicationInfoNullable)
        prefs.put(APPLICATION_INFO_KEY, json)
    }

}
