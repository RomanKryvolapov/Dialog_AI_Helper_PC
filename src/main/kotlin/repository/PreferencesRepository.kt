package repository

import applicationInfoAdapter
import defaultApplicationInfo
import mappers.ApplicationInfoMapper
import models.ApplicationInfo
import models.ApplicationLanguage
import models.TranslateWithGoogleAiModelsEnum
import prefs

object PreferencesRepository {

    const val APPLICATION_INFO_KEY = "APPLICATION_INFO_KEY"

    fun getLastSelectedDevice(): String = getAppInfo().lastSelectedDevice
    fun getSelectedFromLanguage(): ApplicationLanguage = getAppInfo().selectedFromLanguage
    fun getSelectedModel(): TranslateWithGoogleAiModelsEnum = getAppInfo().selectedModel
    fun getSelectedToLanguage(): ApplicationLanguage = getAppInfo().selectedToLanguage
    fun getGoogleCloudToken(): String = getAppInfo().googleCloudToken
    fun getPrompt(): String = getAppInfo().prompt
    fun getLastOpenedTab(): Int = getAppInfo().lastOpenedTab

    fun setLastSelectedDevice(name: String) {
        saveAppInfo(
            getAppInfo().copy(
                lastSelectedDevice = name
            )
        )
    }

    fun setSelectedModel(model: TranslateWithGoogleAiModelsEnum) {
        saveAppInfo(
            getAppInfo().copy(
                selectedModel = model
            )
        )
    }

    fun setSelectedFromLanguage(lang: ApplicationLanguage) {
        saveAppInfo(
            getAppInfo().copy(
                selectedFromLanguage = lang
            )
        )
    }

    fun setSelectedToLanguage(lang: ApplicationLanguage) {
        saveAppInfo(
            getAppInfo().copy(
                selectedToLanguage = lang
            )
        )
    }

    fun setGoogleCloudToken(token: String) {
        saveAppInfo(
            getAppInfo().copy(
                googleCloudToken = token
            )
        )
    }

    fun setPrompt(prompt: String) {
        saveAppInfo(
            getAppInfo().copy(
                prompt = prompt
            )
        )
    }

    fun setLastOpenedTab(index: Int) {
        saveAppInfo(
            getAppInfo().copy(
                lastOpenedTab = index
            )
        )
    }

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
