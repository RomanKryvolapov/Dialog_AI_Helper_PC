package repository

import com.squareup.moshi.Moshi
import defaultApplicationInfo
import di.utilsModule
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfoNullable
import models.domain.ModelParameters
import models.domain.PromptPartsNullable
import models.domain.PromptSettingsNullable
import org.koin.dsl.koinApplication
import utils.ApplicationInfoEnvOverlay
import utils.EnvSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PromptStorageTest {

    private object NoEnv : EnvSettings {
        override fun getString(key: String): String? = null
        override fun getInt(key: String, min: Int): Int? = null
        override fun getLong(key: String, min: Long): Long? = null
        override fun getBoolean(key: String): Boolean? = null
        override fun <T : Enum<T>> getEnum(key: String, values: Array<T>): T? = null
        override fun noteOverriddenByStoredValue(key: String) {}
    }

    private val overlay = ApplicationInfoEnvOverlay(NoEnv)
    private val mapper = ApplicationInfoMapper()
    private val adapter = koinApplication { modules(utilsModule) }.koin
        .get<Moshi>()
        .adapter(ApplicationInfoNullable::class.java)

    private val builtIn = defaultApplicationInfo.prompts

    @Test
    fun `untouched prompts and parameters are not stored, so a better built-in text reaches them`() {
        val stored = overlay.dropFallbackValues(mapper.map(defaultApplicationInfo))
        assertNull(stored.prompts)
        assertNull(stored.modelParameters)
    }

    @Test
    fun `only what the user changed is stored, and it survives a save and a load`() {
        val edited = defaultApplicationInfo.copy(
            prompts = builtIn.copy(previousPhrases = 2, text = builtIn.text.copy(system = "my instructions")),
            modelParameters = mapOf(
                "gpt-6.1-terra" to ModelParameters(thinking = "low"),
                "gemma-4-31b-it" to ModelParameters(),
            ),
        )
        val stored = overlay.dropFallbackValues(mapper.map(edited))
        assertEquals(PromptSettingsNullable(previousPhrases = 2, text = PromptPartsNullable(system = "my instructions")), stored.prompts)
        assertEquals(mapOf("gpt-6.1-terra" to ModelParameters(thinking = "low")), stored.modelParameters)

        val loaded = mapper.reverse(assertNotNull(adapter.fromJson(adapter.toJson(stored))))
        assertEquals("my instructions", loaded.prompts.text.system)
        assertEquals(2, loaded.prompts.previousPhrases)
        assertEquals(builtIn.text.user, loaded.prompts.text.user)
        assertEquals(builtIn.voice, loaded.prompts.voice)
        assertEquals(mapOf("gpt-6.1-terra" to ModelParameters(thinking = "low")), loaded.modelParameters)
    }

    @Test
    fun `settings saved with the old prompt library load on the built-in prompts, keys kept`() {
        val saved = """
            {"prompt":{"name":"Answer the question","prompt":"Answer {{source_text}}"},
             "promptsMap":{"Translate text":{"name":"Translate text","prompt":"{{source_text}}"}},
             "openAiToken":"sk-test"}
        """.trimIndent()
        val info = mapper.reverse(assertNotNull(adapter.fromJson(saved)))
        assertEquals("sk-test", info.openAiToken)
        assertEquals(builtIn, info.prompts)
    }

    @Test
    fun `a part saved blank reads as the built-in one`() {
        val info = mapper.reverse(ApplicationInfoNullable(prompts = PromptSettingsNullable(voice = PromptPartsNullable(system = "  "))))
        assertEquals(builtIn.voice.system, info.prompts.voice.system)
    }
}
