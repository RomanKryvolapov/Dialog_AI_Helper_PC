package repository

import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfo
import models.domain.ApplicationInfoNullable
import utils.ApplicationInfoEnvOverlay
import java.util.prefs.PreferenceChangeEvent
import java.util.prefs.PreferenceChangeListener
import java.util.prefs.Preferences

class PreferencesRepositoryImpl(
    private val prefs: Preferences,
    private val applicationInfoMapper: ApplicationInfoMapper,
    private val applicationInfoEnvOverlay: ApplicationInfoEnvOverlay,
    private val applicationInfoAdapter: JsonAdapter<ApplicationInfoNullable>,
) : PreferencesRepository {

    companion object {
        const val APPLICATION_INFO_KEY = "APPLICATION_INFO_KEY"
        private const val CHUNK_COUNT_KEY = "APPLICATION_INFO_CHUNK_COUNT"
        private const val CHUNK_KEY_PREFIX = "APPLICATION_INFO_CHUNK_"

        // java.util.prefs caps a single value at Preferences.MAX_VALUE_LENGTH (8192 chars);
        // the serialized ApplicationInfo (with all prompts) exceeds that, so we store it in chunks.
        private const val CHUNK_SIZE = 3000
    }

    override val appInfoFlow: Flow<ApplicationInfo> = callbackFlow {
        val listener = PreferenceChangeListener { event: PreferenceChangeEvent ->
            if (event.key == CHUNK_COUNT_KEY) {
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
        val stored = try {
            val json = readJson()
            if (json.isNullOrBlank()) ApplicationInfoNullable()
            else applicationInfoAdapter.fromJson(json) ?: ApplicationInfoNullable()
        } catch (e: Exception) {
            e.printStackTrace()
            ApplicationInfoNullable()
        }
        return applicationInfoMapper.reverse(applicationInfoEnvOverlay.fillFromEnv(stored))
    }

    override fun saveAppInfo(applicationInfo: ApplicationInfo) {
        val applicationInfoNullable = applicationInfoMapper.map(applicationInfo)
        writeJson(applicationInfoAdapter.toJson(applicationInfoEnvOverlay.dropFallbackValues(applicationInfoNullable)))
    }

    override fun clear() {
        writeJson(applicationInfoAdapter.toJson(ApplicationInfoNullable()))
    }

    /** Reassembles the JSON from chunk keys; falls back to the legacy single-key value. */
    private fun readJson(): String? {
        val count = prefs.getInt(CHUNK_COUNT_KEY, -1)
        if (count < 0) {
            return prefs.get(APPLICATION_INFO_KEY, "").takeIf { it.isNotEmpty() }
        }
        return buildString {
            for (i in 0 until count) append(prefs.get(CHUNK_KEY_PREFIX + i, ""))
        }
    }

    /** Splits the JSON across chunk keys so no single value exceeds the prefs limit. */
    private fun writeJson(json: String) {
        val oldCount = prefs.getInt(CHUNK_COUNT_KEY, 0)
        val chunks = if (json.isEmpty()) listOf("") else json.chunked(CHUNK_SIZE)
        for (i in chunks.indices) {
            prefs.put(CHUNK_KEY_PREFIX + i, chunks[i])
        }
        for (i in chunks.size until oldCount) {
            prefs.remove(CHUNK_KEY_PREFIX + i)
        }
        prefs.remove(APPLICATION_INFO_KEY) // drop the legacy single value once migrated
        prefs.putInt(CHUNK_COUNT_KEY, chunks.size) // written last: triggers the change listener
    }

}
