package utils

import VOICE_AUDIO_MARK
import defaultApplicationInfo
import models.domain.PromptParts
import models.domain.PromptSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationPromptsTest {

    private val prompts = defaultApplicationInfo.prompts.copy(
        previousPhrases = 2,
        text = PromptParts(
            system = "INSTRUCTIONS",
            user = "S[{{summary}}] P[{{previous_phrases}}] X[{{phrase}}]",
            previousPhrase = "({{said}}|{{translated}})",
        ),
        voice = PromptParts(
            system = "VOICE INSTRUCTIONS",
            user = "S[{{summary}}] P[{{previous_phrases}}] A[{{audio}}]",
            previousPhrase = "<{{said}}>",
        ),
    )

    /** A conversation of three phrases, the last not translated yet, with a summary already written. */
    private fun conversation(): ConversationContext.Snapshot {
        val context = ConversationContext()
        val settings = ConversationContext.Settings(window = 2, summarize = true, summaryEvery = 6)
        context.add("one", settings).turn.translation = "раз"
        context.add("two", settings).turn.translation = "два"
        val fold = context.add("three", settings).fold!!
        context.foldDone(fold, "SUMMARY")
        return context.add("four", settings).before
    }

    @Test
    fun `a text request is the instructions plus the message with every placeholder filled`() {
        val prompt = TranslationPrompts.text(prompts, conversation(), "four")
        assertEquals("INSTRUCTIONS", prompt.system)
        assertEquals("S[SUMMARY] P[(two|два)\n(three|)] X[four]", prompt.user)
    }

    @Test
    fun `a summary switched off is not sent`() {
        val off = prompts.copy(summary = prompts.summary.copy(enabled = false))
        assertEquals("S[] P[(two|два)\n(three|)] X[four]", TranslationPrompts.text(off, conversation(), "four").user)
    }

    @Test
    fun `no phrases are collected for a summary that is off or that no method could write, and an off one is not sent`() {
        val off = prompts.copy(summary = prompts.summary.copy(enabled = false))
        assertEquals(false, TranslationPrompts.contextSettings(off, canSummarize = true).summarize)
        assertEquals(false, TranslationPrompts.contextSettings(prompts, canSummarize = false).summarize)
        assertEquals(true, TranslationPrompts.contextSettings(prompts, canSummarize = true).summarize)
        assertEquals("", TranslationPrompts.summaryInRequest(off, "written before it was switched off"))
        assertEquals("kept", TranslationPrompts.summaryInRequest(prompts, "kept"))
    }

    @Test
    fun `a voice request marks where the recording goes and uses its own phrase format`() {
        val prompt = TranslationPrompts.voice(prompts, conversation())
        assertEquals("VOICE INSTRUCTIONS", prompt.system)
        assertEquals("S[SUMMARY] P[<two>\n<three>] A[$VOICE_AUDIO_MARK]", prompt.user)
    }

    @Test
    fun `a summary request gets the summary so far and the phrases to fold in`() {
        val context = ConversationContext()
        val settings = ConversationContext.Settings(window = 1, summarize = true, summaryEvery = 6)
        context.add("one", settings).turn.translation = "раз"
        val fold = context.add("two", settings).fold!!
        val custom = prompts.copy(summary = prompts.summary.copy(system = "SUM", user = "OLD[{{summary}}] NEW[{{new_phrases}}]"))
        val prompt = TranslationPrompts.summary(custom, fold)
        assertEquals("SUM", prompt.system)
        assertEquals("OLD[] NEW[(one|раз)]", prompt.user)
    }

    @Test
    fun `what was said is never read as a placeholder`() {
        val context = ConversationContext()
        val settings = ConversationContext.Settings(window = 2, summarize = false, summaryEvery = 6)
        context.add("say {{phrase}} and {{summary}}", settings)
        val prompt = TranslationPrompts.text(prompts, context.add("next", settings).before, "next {{previous_phrases}}")
        assertEquals("S[] P[(say {{phrase}} and {{summary}}|)] X[next {{previous_phrases}}]", prompt.user)
    }

    @Test
    fun `every built-in prompt part passes the check the settings screens make`() {
        val builtIn: PromptSettings = defaultApplicationInfo.prompts
        val parts = listOf(
            PromptPart.INSTRUCTIONS to builtIn.text.system,
            PromptPart.TEXT_MESSAGE to builtIn.text.user,
            PromptPart.PREVIOUS_PHRASE to builtIn.text.previousPhrase,
            PromptPart.INSTRUCTIONS to builtIn.summary.system,
            PromptPart.SUMMARY_MESSAGE to builtIn.summary.user,
            PromptPart.INSTRUCTIONS to builtIn.voice.system,
            PromptPart.VOICE_MESSAGE to builtIn.voice.user,
            PromptPart.PREVIOUS_PHRASE to builtIn.voice.previousPhrase,
        )
        parts.forEach { (part, text) -> assertNull(promptProblem(part, text), "$part: ${text.take(40)}") }
        assertTrue("{{audio}}" in builtIn.voice.user)
    }

    @Test
    fun `the settings screens refuse a part that would send an unfilled placeholder or lose its input`() {
        assertTrue(promptProblem(PromptPart.INSTRUCTIONS, "Translate {{phrase}}")!!.startsWith("The instructions take no placeholders"))
        assertTrue(promptProblem(PromptPart.TEXT_MESSAGE, "<phrase>{{phrse}}</phrase>")!!.startsWith("Unknown placeholder {{phrse}}"))
        assertTrue(promptProblem(PromptPart.TEXT_MESSAGE, "<summary>{{summary}}</summary>")!!.startsWith("{{phrase}} is missing"))
        assertTrue(promptProblem(PromptPart.SUMMARY_MESSAGE, "{{summary}}")!!.startsWith("{{new_phrases}} is missing"))
        assertNull(promptProblem(PromptPart.VOICE_MESSAGE, "{{ summary }} and no audio mark"))
        assertNull(promptProblem(PromptPart.TEXT_MESSAGE, ""), "blank means back to the built-in text")
    }
}
