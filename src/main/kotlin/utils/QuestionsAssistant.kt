package utils

import backgroundThreadScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import models.domain.ConversationTurn
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.ModelParameters
import models.domain.ModelPrompt
import models.domain.effectiveParameters
import models.domain.resolveLlmModel
import org.slf4j.LoggerFactory
import repository.CloudRepository
import repository.PreferencesRepository

/** Where the questions found and their answers are shown (the Questions screen). */
interface QuestionsDisplay {
    /** A new question with its answer so far; the row returned is passed to [update] as the answer grows. */
    fun add(question: String, answer: String): Int
    fun update(row: Int, question: String, answer: String)
    fun clear()
}

/**
 * The Questions mode. While it is on, every phrase heard sends the latest phrases, the conversation summary and the
 * questions answered so far to the chosen model, which answers a new finished question or says there is none
 * ([QuestionPrompts]). One request at a time: the phrases heard meanwhile are covered by the next one, sent as soon
 * as it returns. A question still being asked ("wait") is asked about once more after [QuestionPrompts.SILENCE_SECONDS]
 * without a new phrase, so a last phrase without punctuation is not left waiting for a sequel that never comes.
 */
class QuestionsAssistant(
    private val cloud: CloudRepository,
    private val preferences: PreferencesRepository,
    private val rateLimiters: RateLimiters,
    private val lastRequests: LastRequests,
    private val display: QuestionsDisplay,
) {

    private companion object {
        /** Enough for the largest phrase window the settings allow. */
        const val KEPT_PHRASES = 20
        const val MAX_HISTORY = 20
        /** The tested request ran with room for a 450-token reply. */
        const val REPLY_TOKENS = 450
        const val REQUEST_TIMEOUT_MILLIS = 60_000L
    }

    private val log = LoggerFactory.getLogger("Questions")
    private val lock = Any()
    private val heard = ArrayDeque<ConversationTurn>()
    private val answered = ArrayList<QuestionPrompts.Answered>()
    private var summary = ""
    private var inFlight = false
    private var pending = false
    /** Bumped by [clear]: a reply to a request from before it is not shown. */
    private var epoch = 0L
    private var phrasesHeard = 0L
    private var silenceCheck: Job? = null

    /** [turn] has its text now; [currentSummary] is the conversation summary as it stands. */
    fun phraseHeard(turn: ConversationTurn, currentSummary: String) {
        if (!preferences.getAppInfo().questions.enabled || turn.source.isBlank()) return
        val send = synchronized(lock) {
            // A phrase Whisper wrote down that a voice model then heard again: its text is updated in place.
            if (heard.any { it === turn }) return
            heard.addLast(turn)
            while (heard.size > KEPT_PHRASES) heard.removeFirst()
            summary = currentSummary
            phrasesHeard += 1
            silenceCheck?.cancel()
            silenceCheck = null
            if (inFlight) {
                pending = true
                false
            } else {
                inFlight = true
                true
            }
        }
        if (send) launchRequest(afterSilence = false)
    }

    /** A new dialog: no phrases, no history, nothing shown; a reply still on its way is dropped. */
    fun clear() {
        synchronized(lock) {
            heard.clear()
            answered.clear()
            summary = ""
            epoch += 1
            pending = false
            silenceCheck?.cancel()
            silenceCheck = null
        }
        display.clear()
    }

    private fun launchRequest(afterSilence: Boolean) {
        val mine = synchronized(lock) { epoch }
        backgroundThreadScope.launch {
            val status = try {
                ask(afterSilence, mine)
            } catch (e: TimeoutCancellationException) {
                // Caught before CancellationException, which it is: a stalled reply must not stop the mode.
                log.warn("Questions: no full reply in {} s, this check is dropped", REQUEST_TIMEOUT_MILLIS / 1000)
                null
            } catch (e: CancellationException) {
                synchronized(lock) { inFlight = false; pending = false }
                throw e
            } catch (e: Throwable) {
                val failure = classifyFailure(e)
                log.warn("Questions request failed ({}): {}", failure.kind, failure.detail)
                null
            }
            val again = synchronized(lock) {
                when {
                    epoch != mine -> { inFlight = false; pending = false; return@launch }
                    pending -> { pending = false; true }
                    else -> { inFlight = false; false }
                }
            }
            if (again) launchRequest(afterSilence = false)
            else if (status == "wait" && !afterSilence) scheduleSilenceCheck(mine)
        }
    }

    private fun scheduleSilenceCheck(mine: Long) {
        val heardThen = synchronized(lock) { phrasesHeard }
        val job = backgroundThreadScope.launch {
            delay(QuestionPrompts.SILENCE_SECONDS * 1000L)
            val send = synchronized(lock) {
                if (epoch != mine || phrasesHeard != heardThen || inFlight) false else { inFlight = true; true }
            }
            if (send) launchRequest(afterSilence = true)
        }
        synchronized(lock) { silenceCheck = job }
    }

    /** One request; returns the reply's status, or null when none was sent. */
    private suspend fun ask(afterSilence: Boolean, mine: Long): String? {
        val appInfo = preferences.getAppInfo()
        val settings = appInfo.questions
        val model = resolveLlmModel(settings.modelId)?.takeIf { it.readsText }
        if (model == null) {
            log.warn("Questions: the chosen model {} cannot answer text", settings.modelId)
            return null
        }
        val key = keyFor(model, appInfo.googleCloudToken, appInfo.openAiToken, appInfo.claudeToken)
        if (key.isBlank()) {
            log.warn("Questions: no {} API key for {}", model.engine.type, model.title)
            return null
        }
        val (speech, history, summaryNow) = synchronized(lock) {
            Triple(
                heard.filter { it.source.isNotEmpty() }.takeLast(settings.phrases.coerceIn(1, KEPT_PHRASES)),
                answered.mapIndexed { i, entry -> if (i >= answered.size - settings.fullAnswers) entry else entry.copy(answer = null) },
                summary,
            )
        }
        if (speech.isEmpty()) return null
        val prompt = QuestionPrompts.request(settings, summaryNow, history, speech, afterSilence)
        val limiter = rateLimiters.forModel(model)
        while (true) {
            val wait = limiter.tryReserve((prompt.system.length + prompt.user.length) / 4 + REPLY_TOKENS)
            if (wait == 0L) break
            if (wait > SHORT_WAIT_MILLIS) {
                log.info("Questions: {} is at its rate limit for {} s, this phrase is not checked", model.title, wait / 1000)
                return null
            }
            delay(wait)
        }
        lastRequests.questionsSent(LastRequests.Sent(model.title, prompt))

        var row: Int? = null
        var shown: String? = null
        var repeated = false
        var last = ""
        withTimeout(REQUEST_TIMEOUT_MILLIS) {
            stream(model, key, prompt, appInfo.modelParameters[model.id]).collect { text ->
                last = text
                if (synchronized(lock) { epoch != mine }) return@collect
                val reply = QuestionPrompts.parse(text)
                if (reply.status != "answer" || !reply.questionComplete || repeated) return@collect
                val question = reply.question.orEmpty()
                val current = row
                if (current == null) {
                    if (isRepeat(question)) {
                        repeated = true
                        log.info("Questions: \"{}\" was answered already, not shown again", question)
                        return@collect
                    }
                    row = display.add(question, reply.answer.orEmpty().ifBlank { "..." })
                    shown = question
                } else {
                    display.update(current, question, reply.answer.orEmpty().ifBlank { "..." })
                }
            }
        }
        val reply = QuestionPrompts.parse(last)
        log.debug("Questions reply: {}", reply.status)
        val question = shown
        val answer = reply.answer.orEmpty()
        val current = row
        if (current != null && question != null && answer.isNotBlank() && synchronized(lock) { epoch == mine }) {
            display.update(current, question, answer)
            synchronized(lock) {
                answered += QuestionPrompts.Answered(question, answer)
                while (answered.size > MAX_HISTORY) answered.removeAt(0)
            }
        }
        return reply.status
    }

    private fun isRepeat(question: String): Boolean = synchronized(lock) {
        answered.any { QuestionPrompts.overlap(it.question, question) >= QuestionPrompts.SAME_QUESTION_OVERLAP }
    }

    /**
     * The chosen model's settings from the LLM page, except that questions run at temperature 0 with room for a
     * 450-token reply unless the page sets them: that is how the design was tested.
     */
    private fun stream(model: LlmModel, key: String, prompt: ModelPrompt, chosen: ModelParameters?): Flow<String> {
        val own = (chosen ?: ModelParameters()).let {
            it.copy(temperature = it.temperature ?: 0.0, maxOutputTokens = it.maxOutputTokens ?: REPLY_TOKENS)
        }
        val parameters = effectiveParameters(model.parameterSupport, own)
        return when (model.engine) {
            LlmModelEngine.GOOGLE -> cloud.streamGoogleAI(model.id, key, prompt, parameters)
            LlmModelEngine.OPENAI -> cloud.streamOpenAI(model.id, key, prompt, parameters)
            LlmModelEngine.CLAUDE -> cloud.streamClaude(model.id, key, prompt, parameters)
        }
    }

    private fun keyFor(model: LlmModel, google: String, openAi: String, claude: String): String = when (model.engine) {
        LlmModelEngine.GOOGLE -> google
        LlmModelEngine.OPENAI -> openAi
        LlmModelEngine.CLAUDE -> claude
    }
}
