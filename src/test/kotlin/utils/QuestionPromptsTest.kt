package utils

import models.domain.ConversationTurn
import models.domain.QuestionsSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuestionPromptsTest {

    private val settings = QuestionsSettings(
        enabled = true,
        modelId = "gpt-6.1-terra",
        phrases = 8,
        fullAnswers = 3,
        system = QuestionPrompts.INSTRUCTIONS,
        user = QuestionPrompts.MESSAGE,
    )

    private fun turn(text: String, speaker: String? = null) = ConversationTurn(text).apply { this.speaker = speaker }

    @Test
    fun `a request carries the summary, the questions answered and the latest phrases, newest last`() {
        val prompt = QuestionPrompts.request(
            settings,
            summary = "",
            answered = listOf(
                QuestionPrompts.Answered("What is a thread?", null),
                QuestionPrompts.Answered("Why PostgreSQL?", "Because of its indexes."),
            ),
            speech = listOf(turn("So which index", "S1"), turn("would you add?", "S1")),
            afterSilence = false,
            lastReplaceable = true,
        )
        assertEquals(QuestionPrompts.INSTRUCTIONS, prompt.system)
        assertTrue("<summary>\n(none yet)\n</summary>" in prompt.user)
        assertTrue("<qa>\n<question>What is a thread?</question>\n</qa>" in prompt.user, "an older entry keeps only its question")
        assertTrue(
            "<qa replaceable=\"yes\">\n<question>Why PostgreSQL?</question>\n<answer>Because of its indexes.</answer>\n</qa>" in prompt.user,
            "only the last entry is offered for replacing",
        )
        assertTrue(prompt.user.endsWith("<speech>\nS1: So which index\nS1: would you add?\n</speech>"))
        val again = QuestionPrompts.request(settings, "", emptyList(), listOf(turn("at least once")), afterSilence = true, lastReplaceable = false)
        assertTrue(again.user.endsWith("<speech>\nat least once\n</speech>\n\n<silence>3 s</silence>"), "no labels: bare lines")
        assertTrue("<answered>\n(none yet)\n</answered>" in again.user)
    }

    @Test
    fun `a reply is read whole or while it streams, and only an answer shows anything`() {
        val full = QuestionPrompts.parse(
            "<status>answer</status>\n<question>1) What is a race?</question>\n<answer>Two threads touch the same state.</answer>\n<status>none</status>"
        )
        assertEquals("answer", full.status)
        assertEquals("1) What is a race?", full.question)
        assertTrue(full.questionComplete)
        assertEquals("Two threads touch the same state.", full.answer)

        val streaming = QuestionPrompts.parse("<status>answer</status>\n<question>What is a ra")
        assertEquals("What is a ra", streaming.question)
        assertEquals(false, streaming.questionComplete)
        assertNull(streaming.answer)

        assertEquals("none", QuestionPrompts.parse("<status>none</status>").status)
        assertEquals("wait", QuestionPrompts.parse("<STATUS> wait </STATUS>").status)
        assertNull(QuestionPrompts.parse("<status>none</status>").answer)
    }

    @Test
    fun `a question asked again in other words counts as the same one, a different one does not`() {
        val asked = "How would you index a table queried by user id and date?"
        assertTrue(QuestionPrompts.overlap(asked, "How would you index a table queried by user id and by date?") >= QuestionPrompts.SAME_QUESTION_OVERLAP)
        assertTrue(QuestionPrompts.overlap(asked, "Why did you put user id first in that index?") < QuestionPrompts.SAME_QUESTION_OVERLAP)
        listOf(
            "What is the difference between a process and a thread?" to "What is the difference between a thread and a coroutine?",
            "What is the difference between an abstract class and an interface?" to "What is the difference between an interface and a trait?",
            "How does garbage collection work in Java?" to "How does garbage collection work in Go?",
            "What happens when inserting into a hash map?" to "What happens when deleting from a hash map?",
            "How do you handle errors in Kotlin coroutines?" to "How do you handle cancellation in Kotlin coroutines?",
        ).forEach { (first, second) ->
            assertTrue(QuestionPrompts.overlap(first, second) < QuestionPrompts.SAME_QUESTION_OVERLAP, "taken as the same: $second")
        }
        assertEquals(0.0, QuestionPrompts.overlap("", asked))
    }

    @Test
    fun `the built-in questions message passes the check the settings screen makes`() {
        assertNull(promptProblem(PromptPart.QUESTIONS_MESSAGE, QuestionPrompts.MESSAGE))
        assertNull(promptProblem(PromptPart.INSTRUCTIONS, QuestionPrompts.INSTRUCTIONS))
    }
}
