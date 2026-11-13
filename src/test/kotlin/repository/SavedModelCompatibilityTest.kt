package repository

import com.squareup.moshi.Moshi
import di.utilsModule
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfoNullable
import models.domain.Hearing
import models.domain.LlmModelEngine
import models.domain.VoiceRecognitionEngine
import models.domain.allHearings
import models.domain.allLlmModels
import models.domain.resolveLlmModel
import models.domain.resolveMethodChain
import models.domain.resolveTranslationMethod
import models.domain.textModels
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedModelCompatibilityTest {

    private val adapter = koinApplication { modules(utilsModule) }.koin
        .get<Moshi>()
        .adapter(ApplicationInfoNullable::class.java)

    @Test
    fun `settings saved with a model the catalog dropped still load, keys included`() {
        val saved = """
            {"selectedModel":{"id":"gpt-5.6-terra","description":"OpenAI: gpt-5.6-terra","engine":"OPENAI","openAiModel":"GPT_5_6_TERRA"},
             "openAiToken":"sk-test","lastSelectedDevice":"Voicemeeter Out B1"}
        """.trimIndent()
        val info = assertNotNull(adapter.fromJson(saved))
        assertEquals("sk-test", info.openAiToken)
        assertEquals("Voicemeeter Out B1", info.lastSelectedDevice)
        assertEquals("gpt-5.6-terra", info.selectedModel?.id)
        assertNull(info.selectedModel?.openAiModel, "an unknown model constant must read as null")
    }

    @Test
    fun `a recogniser the app no longer offers reads as unset instead of wiping the settings`() {
        val info = assertNotNull(adapter.fromJson("""{"voiceRecognitionEngine":"GEMINI_VOICE","openAiToken":"sk-test"}"""))
        assertNull(info.voiceRecognitionEngine)
        assertEquals("sk-test", info.openAiToken)
        assertEquals(VoiceRecognitionEngine.WHISPER_STREAMING,
            adapter.fromJson("""{"voiceRecognitionEngine":"WHISPER_STREAMING"}""")?.voiceRecognitionEngine)
    }

    @Test
    fun `a saved method chain survives a round trip through the stored shape`() {
        val mapper = ApplicationInfoMapper()
        val keys = listOf("voice:gemini-3.5-flash-lite", "voice:gpt-audio-1.5", "whisper-batch:gpt-6.1-terra")
        val info = mapper.reverse(ApplicationInfoNullable(translationMethods = keys))
        assertEquals(keys, info.translationMethods.map { it.key })
        val stored = mapper.map(info)
        assertEquals(keys, stored.translationMethods)
        assertNull(stored.selectedModel, "the old single-model field is never written again")
        assertNull(stored.voiceRecognitionEngine, "nor the old separate recogniser")
        assertEquals(stored, adapter.fromJson(adapter.toJson(stored)))
    }

    @Test
    fun `a method is a hearing and, for a recogniser, the model that translates`() {
        val voice = assertNotNull(resolveTranslationMethod("voice:gpt-audio-1.5"))
        assertTrue(voice.voice)
        assertNull(voice.recognizer, "a model that hears audio needs no recogniser")
        val text = assertNotNull(resolveTranslationMethod("whisper-streaming:claude-sonnet-5-5"))
        assertEquals(VoiceRecognitionEngine.WHISPER_STREAMING, text.recognizer)
        assertEquals("claude-sonnet-5-5", text.model.id)

        assertNull(resolveTranslationMethod("voice:gpt-6.1-terra"), "Terra refuses audio")
        assertNull(resolveTranslationMethod("voice:gemma-4-31b-it"), "Gemma does not hear audio")
        assertNull(resolveTranslationMethod("whisper-batch:gpt-audio-1.5"), "GPT-Audio does not answer text alone")
        assertNull(resolveTranslationMethod("nonsense:gpt-6.1-terra"))

        // Keys of the first chains: "text:" on the recogniser that was a separate setting then.
        assertEquals("whisper-streaming:gpt-6.1-terra", resolveTranslationMethod("text:gpt-5.6-terra", VoiceRecognitionEngine.WHISPER_STREAMING)?.key)
        assertEquals("whisper-batch:gpt-6.1-terra", resolveTranslationMethod("gpt-6.1-terra")?.key)
        assertEquals("speechmatics:gpt-6.1-terra", resolveTranslationMethod("speechmatics:gpt-6.1-terra")?.key, "Speechmatics is a recogniser now")
    }

    @Test
    fun `a saved chain keeps known methods once each, in order, three at most`() {
        val chain = resolveMethodChain(
            listOf(
                "whisper-streaming:gpt-6.1-terra",
                "whisper-streaming:gpt-6.1-terra", // repeated
                "voice:no-such-model",
                "voice:gpt-audio-1.5", // any method can be a reserve: switching to it rebuilds the audio input
                "sherpa-onnx:claude-sonnet-5-5",
                "whisper-streaming:gemma-4-31b-it", // past the third
            )
        )
        assertEquals(
            listOf("whisper-streaming:gpt-6.1-terra", "voice:gpt-audio-1.5", "sherpa-onnx:claude-sonnet-5-5"),
            chain.map { it.key },
        )
    }

    @Test
    fun `the first level offers the models that hear audio, then the working recognisers`() {
        val hearings = allHearings()
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gpt-audio-1.5"),
            hearings.filterIsInstance<Hearing.Voice>().map { it.model.id },
        )
        assertEquals(
            VoiceRecognitionEngine.entries,
            hearings.filterIsInstance<Hearing.Recognition>().map { it.recognizer },
            "every recogniser is on offer, Speechmatics included",
        )
        assertTrue(textModels().none { it.id == "gpt-audio-1.5" })
        assertTrue(textModels().any { it.id == "gpt-6.1-terra" })
    }

    @Test
    fun `a dropped model resolves to its successor from the same provider`() {
        val expected = mapOf(
            "gpt-5.6-terra" to "gpt-6.1-terra",
            "gpt-5.4-nano" to "gpt-6.1-terra",
            "gemini-2.0-flash" to "gemini-3.5-flash-lite",
            "gemini-3.6-flash" to "gemini-3.5-flash-lite",
            "gemini-2.5-flash-lite" to "gemini-3.5-flash-lite",
            "gemma-3-27b-it" to "gemma-4-31b-it",
            "claude-haiku-4-5" to "claude-sonnet-5-5",
            "claude-sonnet-5" to "claude-sonnet-5-5",
        )
        expected.forEach { (old, new) -> assertEquals(new, resolveLlmModel(old)?.id, old) }
        assertEquals(LlmModelEngine.OPENAI, resolveLlmModel("gpt-5.6-luna")?.engine)
    }

    @Test
    fun `current models resolve to themselves and unknown ones to nothing`() {
        allLlmModels().forEach { assertEquals(it, resolveLlmModel(it.id)) }
        assertNull(resolveLlmModel("no-such-model"))
    }

    @Test
    fun `settings saved before the unused send-text settings were removed still load`() {
        val saved = """{"sendTextEverySymbols":100,"sendTextEveryMilliseconds":10000,"openAiToken":"sk-test","questionLanguage":"ru"}"""
        val info = ApplicationInfoMapper().reverse(assertNotNull(adapter.fromJson(saved)))
        assertEquals("sk-test", info.openAiToken)
        assertEquals("ru", info.questionLanguage)
    }
}
