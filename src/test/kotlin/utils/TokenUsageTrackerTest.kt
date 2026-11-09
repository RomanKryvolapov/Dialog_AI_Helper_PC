package utils

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage
import dev.langchain4j.model.output.TokenUsage
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenUsageTrackerTest {

    @Test
    fun `answers add up per model and in total, and Clear dialog starts from zero`() {
        val tracker = TokenUsageTracker()
        val dialog = tracker.currentDialog()
        tracker.record("gpt-6.1-terra", TokenUsage(900, 60), dialog)
        tracker.record("gpt-6.1-terra", TokenUsage(1_100, 40), dialog)
        tracker.record(
            "gemini-3.5-flash-lite",
            GoogleAiGeminiTokenUsage.builder().inputTokenCount(800).outputTokenCount(50).thoughtsTokenCount(30).build(),
            dialog,
        )
        tracker.record("claude-sonnet-5-5", null, dialog)

        val totals = tracker.totals.value
        assertEquals(TokenUsageTracker.ModelUsage(input = 2_000, output = 100, answers = 2), totals.byModel["gpt-6.1-terra"])
        assertEquals(80, totals.byModel["gemini-3.5-flash-lite"]?.output, "Gemini's thinking is billed as output")
        assertEquals(2_800, totals.input)
        assertEquals(180, totals.output)
        assertEquals(2_980, totals.total)
        assertEquals(3, totals.answers)
        assertEquals(1, totals.unreported)

        tracker.reset()
        assertEquals(TokenUsageTracker.Totals(), tracker.totals.value)
        tracker.record("gpt-6.1-terra", TokenUsage(500, 20), dialog)
        assertEquals(TokenUsageTracker.Totals(), tracker.totals.value, "an answer to a request sent before Clear dialog")
        tracker.record("gpt-6.1-terra", TokenUsage(500, 20), tracker.currentDialog())
        assertEquals(520, tracker.totals.value.total)
    }
}
