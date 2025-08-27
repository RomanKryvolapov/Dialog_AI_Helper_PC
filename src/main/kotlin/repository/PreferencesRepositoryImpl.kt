package repository

import com.squareup.moshi.JsonAdapter
import defaultApplicationInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable
import java.util.prefs.PreferenceChangeEvent
import java.util.prefs.PreferenceChangeListener
import java.util.prefs.Preferences

class PreferencesRepositoryImpl(
    private val prefs: Preferences,
    private val applicationInfoMapper: ApplicationInfoMapper,
    private val applicationInfoAdapter: JsonAdapter<ApplicationInfoNullable>,
) : PreferencesRepository {

    companion object {
        const val APPLICATION_INFO_KEY = "APPLICATION_INFO_KEY"
    }

    override val appInfoFlow: Flow<ApplicationInfo> = callbackFlow {
        val listener = PreferenceChangeListener { event: PreferenceChangeEvent ->
            if (event.key == APPLICATION_INFO_KEY) {
                try {
                    trySend(getAppInfo())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        prefs.addPreferenceChangeListener(listener)
        trySend(getAppInfo())
        awaitClose {
            prefs.removePreferenceChangeListener(listener)
        }
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
