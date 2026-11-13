package utils

import defaultApplicationInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import models.domain.ApplicationInfo
import models.domain.ConversationTurn
import models.domain.ModelParameters
import models.domain.ModelPrompt
import repository.CloudRepository
import repository.PreferencesRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

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

}
