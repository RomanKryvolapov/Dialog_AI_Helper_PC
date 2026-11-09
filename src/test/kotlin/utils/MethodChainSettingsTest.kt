package utils

import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfoNullable
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.VoiceRecognitionEngine
import repository.PreferencesRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MethodChainSettingsTest {

    private class FakeEnv(private val values: Map<String, String>) : EnvSettings {
        override fun getString(key: String) = values[key]
        override fun getInt(key: String, min: Int) = values[key]?.toIntOrNull()
        override fun getLong(key: String, min: Long) = values[key]?.toLongOrNull()
        override fun getBoolean(key: String) = values[key]?.toBooleanStrictOrNull()
        override fun <T : Enum<T>> getEnum(key: String, values: Array<T>): T? =
            this.values[key]?.let { name -> values.firstOrNull { it.name == name } }
        override fun noteOverriddenByStoredValue(key: String) {}
    }

    private val mapper = ApplicationInfoMapper()

    private fun chainFrom(env: Map<String, String>, stored: ApplicationInfoNullable = ApplicationInfoNullable()) =
        mapper.reverse(ApplicationInfoEnvOverlay(FakeEnv(env)).fillFromEnv(PreferencesRepositoryImpl.migrated(stored)))
            .translationMethods.map { it.key }

    @Test
    fun `the model and recogniser saved before methods existed become method 1`() {
        val saved = ApplicationInfoNullable(
            selectedModel = LlmModel(id = "gpt-5.6-terra", description = null, engine = LlmModelEngine.OPENAI),
            voiceRecognitionEngine = VoiceRecognitionEngine.WHISPER_STREAMING,
        )
        val migrated = PreferencesRepositoryImpl.migrated(saved)
        assertEquals(listOf("text:gpt-5.6-terra"), migrated.translationMethods)
        assertNull(migrated.selectedModel)
        assertEquals(listOf("whisper-streaming:gpt-6.1-terra"), chainFrom(emptyMap(), saved), "on the saved recogniser, with the successor model")
    }

    @Test
    fun `a saved chain of methods the catalog dropped counts as unset`() {
        val saved = ApplicationInfoNullable(translationMethods = listOf("voice:no-such-model"))
        assertNull(PreferencesRepositoryImpl.migrated(saved).translationMethods)
        assertEquals(listOf("whisper-batch:gemma-4-31b-it"), chainFrom(mapOf("DIALOG_LLM_MODEL_ID" to "gemma-4-31b-it"), saved))
    }

    @Test
    fun `env supplies the chain the user has not chosen, the older keys on the env or saved recogniser`() {
        assertEquals(listOf("whisper-batch:gemini-3.5-flash-lite"), chainFrom(emptyMap()), "the built-in default")
        assertEquals(listOf("whisper-batch:gpt-6.1-terra"), chainFrom(mapOf("DIALOG_LLM_MODEL_ID" to "gpt-6.1-terra")))
        assertEquals(
            listOf("whisper-streaming:gpt-6.1-terra"),
            chainFrom(mapOf("DIALOG_LLM_MODEL_ID" to "gpt-6.1-terra", "DIALOG_VOICE_RECOGNITION_ENGINE" to "WHISPER_STREAMING")),
        )
        assertEquals(
            listOf("sherpa-onnx:gpt-6.1-terra"),
            chainFrom(
                mapOf("DIALOG_LLM_MODEL_ID" to "gpt-6.1-terra"),
                ApplicationInfoNullable(voiceRecognitionEngine = VoiceRecognitionEngine.SHERPA_ONNX),
            ),
            "the recogniser saved as a setting of its own",
        )
        assertEquals(
            listOf("voice:gemini-3.5-flash-lite", "voice:gpt-audio-1.5", "whisper-batch:gpt-6.1-terra"),
            chainFrom(
                mapOf(
                    "DIALOG_TRANSLATION_METHODS" to " voice:gemini-3.5-flash-lite, voice:gpt-audio-1.5 ,whisper-batch:gpt-6.1-terra,bogus",
                    "DIALOG_LLM_MODEL_ID" to "claude-sonnet-5-5",
                )
            ),
            "the methods key wins over the older one; unknown entries are ignored",
        )
        assertEquals(
            listOf("whisper-batch:claude-sonnet-5-5"),
            chainFrom(
                mapOf("DIALOG_TRANSLATION_METHODS" to "voice:gemini-3.5-flash-lite"),
                ApplicationInfoNullable(translationMethods = listOf("whisper-batch:claude-sonnet-5-5")),
            ),
            "a chain the user chose beats .env",
        )
    }

    @Test
    fun `a recogniser chosen on its own, with the default model, keeps working after the update`() {
        val saved = ApplicationInfoNullable(voiceRecognitionEngine = VoiceRecognitionEngine.WHISPER_STREAMING)
        assertEquals(listOf("whisper-streaming:gemini-3.5-flash-lite"), chainFrom(emptyMap(), saved))
        val overlay = ApplicationInfoEnvOverlay(FakeEnv(emptyMap()))
        val reread = mapper.reverse(overlay.fillFromEnv(PreferencesRepositoryImpl.migrated(saved)))
        val stored = overlay.dropFallbackValues(mapper.map(reread))
        assertEquals(listOf("whisper-streaming:gemini-3.5-flash-lite"), stored.translationMethods, "the startup save keeps the choice")
        assertNull(stored.voiceRecognitionEngine)
        assertEquals(listOf("whisper-streaming:gemini-3.5-flash-lite"), chainFrom(emptyMap(), stored), "and reads it back")

        val envOnly = mapOf("DIALOG_VOICE_RECOGNITION_ENGINE" to "SHERPA_ONNX")
        assertEquals(listOf("sherpa-onnx:gemini-3.5-flash-lite"), chainFrom(envOnly), "a recogniser set only in .env")
        val envOverlay = ApplicationInfoEnvOverlay(FakeEnv(envOnly))
        val fromEnv = mapper.map(mapper.reverse(envOverlay.fillFromEnv(ApplicationInfoNullable())))
        assertNull(envOverlay.dropFallbackValues(fromEnv).translationMethods, "is not stored, so .env stays in charge")
    }

    @Test
    fun `only a chain that differs from the fallback is stored, and the old recogniser never again`() {
        val overlay = ApplicationInfoEnvOverlay(FakeEnv(mapOf("DIALOG_LLM_MODEL_ID" to "gpt-6.1-terra")))
        val same = ApplicationInfoNullable(translationMethods = listOf("whisper-batch:gpt-6.1-terra"))
        assertNull(overlay.dropFallbackValues(same).translationMethods, "the .env chain, as the app saves it")
        val chosen = ApplicationInfoNullable(translationMethods = listOf("whisper-streaming:gpt-6.1-terra"))
        assertEquals(chosen.translationMethods, overlay.dropFallbackValues(chosen).translationMethods)
        val legacy = ApplicationInfoNullable(voiceRecognitionEngine = VoiceRecognitionEngine.WHISPER_STREAMING)
        assertNull(overlay.dropFallbackValues(legacy).voiceRecognitionEngine)
    }
}
