package utils

import defaultApplicationInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import models.domain.ApplicationInfo
import models.domain.ConversationTurn
import models.domain.ModelParameters
import models.domain.ModelPrompt
import repository.CloudRepository
import repository.PreferencesRepository
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestionsAssistantTest {

    private class Prefs : PreferencesRepository {
        private val info = defaultApplicationInfo.copy(
            openAiToken = "test-key",
            questions = defaultApplicationInfo.questions.copy(enabled = true),
        )
        override val appInfoFlow: Flow<ApplicationInfo> = MutableStateFlow(info)
        override fun getAppInfo() = info
        override fun saveAppInfo(applicationInfo: ApplicationInfo) {}
    }

    /** Every reply times out, the way a stream that stalls after its headers does. */
    private class StallingCloud : CloudRepository {
        val requests = AtomicInteger()
        private fun stall(): Flow<String> = flow {
            requests.incrementAndGet()
            withTimeout(10) { delay(1_000) }
            emit("never")
        }
        override fun streamGoogleAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters) = stall()
        override fun streamGoogleAIWithAudio(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters, wav: ByteArray) = stall()
        override fun streamOpenAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters) = stall()
        override fun streamOpenAIWithAudio(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters, wav: ByteArray) = stall()
        override fun streamClaude(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters) = stall()
    }

    private object NoScreen : QuestionsDisplay {
        override fun add(question: String, answer: String) = 0
        override fun update(row: Int, question: String, answer: String) {}
        override fun clear() {}
    }

    @Test
    fun `a request that times out does not stop the next phrases from being checked`() {
        val cloud = StallingCloud()
        val assistant = QuestionsAssistant(cloud, Prefs(), RateLimiters(), LastRequests(), NoScreen)
        repeat(3) { i ->
            assistant.phraseHeard(ConversationTurn("phrase number $i of the call"), "")
            val deadline = System.currentTimeMillis() + 5_000
            while (cloud.requests.get() < i + 1 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            Thread.sleep(200)
        }
        assertEquals(3, cloud.requests.get(), "a timed-out request left the mode stuck")
    }

    /** Answers request N with [steps] N (then "none"), and keeps every request's message. */
    private class ScriptedCloud(vararg steps: Flow<String>) : CloudRepository {
        private val steps = ArrayDeque(steps.toList())
        val messages = CopyOnWriteArrayList<String>()
        private fun next(prompt: ModelPrompt): Flow<String> {
            messages += prompt.user
            return synchronized(steps) { steps.removeFirstOrNull() } ?: flowOf("<status>none</status>")
        }
        override fun streamGoogleAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters) = next(prompt)
        override fun streamGoogleAIWithAudio(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters, wav: ByteArray) = next(prompt)
        override fun streamOpenAI(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters) = next(prompt)
        override fun streamOpenAIWithAudio(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters, wav: ByteArray) = next(prompt)
        override fun streamClaude(model: String, apiKey: String, prompt: ModelPrompt, parameters: ModelParameters) = next(prompt)
    }

    /** The Questions screen as rows of question to answer. */
    private class Rows : QuestionsDisplay {
        val rows = CopyOnWriteArrayList<Pair<String, String>>()
        override fun add(question: String, answer: String): Int = synchronized(rows) { rows += question to answer; rows.lastIndex }
        override fun update(row: Int, question: String, answer: String) { rows[row] = question to answer }
        override fun clear() = rows.clear()
    }

    /** Hears [text] and waits until the request it sends (the [request]-th) has been answered. */
    private fun hear(assistant: QuestionsAssistant, cloud: ScriptedCloud, text: String, request: Int) {
        assistant.phraseHeard(ConversationTurn(text), "")
        val deadline = System.currentTimeMillis() + 5_000
        while (cloud.messages.size < request && System.currentTimeMillis() < deadline) Thread.sleep(20)
        Thread.sleep(300)
    }

    @Test
    fun `a question that goes on after it was answered is answered again in its own row, not in a new one`() {
        val cloud = ScriptedCloud(
            flowOf("<status>answer</status>\n<question>How does garbage collection work in Java?</question>\n<answer>It frees objects nothing refers to.</answer>"),
            flowOf("<status>update</status>\n<question>How does garbage collection work in Java, and what is a stop-the-world pause?</question>\n<answer>It frees objects nothing refers to; a stop-the-world pause halts the application threads meanwhile.</answer>"),
        )
        val screen = Rows()
        val assistant = QuestionsAssistant(cloud, Prefs(), RateLimiters(), LastRequests(), screen)
        hear(assistant, cloud, "How does garbage collection work in java", 1)
        hear(assistant, cloud, "and what is a stop the world pause", 2)
        assertTrue("<qa replaceable=\"yes\">" in cloud.messages[1], "the answer given a moment ago is offered for replacing")
        assertEquals(
            listOf("How does garbage collection work in Java, and what is a stop-the-world pause?" to "It frees objects nothing refers to; a stop-the-world pause halts the application threads meanwhile."),
            screen.rows.toList(),
        )
        hear(assistant, cloud, "Okay", 3)
        val history = cloud.messages[2].substringAfter("<answered>").substringBefore("</answered>")
        assertEquals(1, Regex("<qa[ >]").findAll(history).count(), "the update took the place of the first answer: $history")
        assertTrue("stop-the-world" in history, history)
    }

    @Test
    fun `an update that breaks off puts back the answer it was replacing, and one with nothing before adds a row`() {
        val cloud = ScriptedCloud(
            flowOf("<status>answer</status>\n<question>What is a race condition?</question>\n<answer>Two threads change shared state at once.</answer>"),
            flow {
                emit("<status>update</status>\n<question>What is a race condition, and how do you prevent it?</question>\n<answer>Two thr")
                throw IOException("connection reset")
            },
        )
        val screen = Rows()
        val assistant = QuestionsAssistant(cloud, Prefs(), RateLimiters(), LastRequests(), screen)
        hear(assistant, cloud, "What is a race condition", 1)
        hear(assistant, cloud, "and how do you prevent it", 2)
        assertEquals(listOf("What is a race condition?" to "Two threads change shared state at once."), screen.rows.toList())

        val fresh = ScriptedCloud(flowOf("<status>update</status>\n<question>What is a deadlock?</question>\n<answer>Two threads wait for each other forever.</answer>"))
        val other = Rows()
        hear(QuestionsAssistant(fresh, Prefs(), RateLimiters(), LastRequests(), other), fresh, "What is a deadlock", 1)
        assertEquals(listOf("What is a deadlock?" to "Two threads wait for each other forever."), other.rows.toList())
    }

    @Test
    fun `an update heard well after the phrase the answer was given on gets a row of its own`() {
        val cloud = ScriptedCloud(
            flowOf("<status>answer</status>\n<question>What is a race condition?</question>\n<answer>Two threads change shared state at once.</answer>"),
            flowOf("<status>update</status>\n<question>What is a race condition, and how would you prevent it?</question>\n<answer>With locks or atomic variables.</answer>"),
        )
        val screen = Rows()
        val assistant = QuestionsAssistant(cloud, Prefs(), RateLimiters(), LastRequests(), screen)
        hear(assistant, cloud, "What is a race condition", 1)
        Thread.sleep(6_500) // the user answers; only the other side is heard
        hear(assistant, cloud, "and how would you prevent it", 2)
        assertTrue("replaceable" !in cloud.messages[1], "an answer the user may be reading is not offered for replacing")
        assertEquals(
            listOf(
                "What is a race condition?" to "Two threads change shared state at once.",
                "What is a race condition, and how would you prevent it?" to "With locks or atomic variables.",
            ),
            screen.rows.toList(),
        )
    }
}
