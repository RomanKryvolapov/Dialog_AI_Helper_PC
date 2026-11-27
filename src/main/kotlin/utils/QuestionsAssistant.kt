package utils

import backgroundThreadScope
import models.domain.ConversationTurn
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.ModelParameters
import models.domain.ModelPrompt
import models.domain.effectiveParameters
import models.domain.resolveLlmModel
import repository.CloudRepository
import repository.PreferencesRepository
import repository.streamText

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/** Where the questions found and their answers are shown (the Questions screen). */
interface QuestionsDisplay {
    /** A new question with its answer so far; the row returned is passed to [update] as the answer grows. */
    fun add(question: String, answer: String): Int
    fun update(row: Int, question: String, answer: String)
    /** The answer in [row] is complete and kept. */
    fun settled(row: Int) {}
    fun clear()
}

/**
 * The Questions mode. While it is on, every phrase heard sends the latest phrases, the conversation summary and the
 * questions answered so far to the chosen model, which answers a new finished question or says there is none
 * ([QuestionPrompts]). One request at a time: the phrases heard meanwhile are covered by the next one, sent as soon
 * as it returns. A question still being asked ("wait") is asked about once more after [QuestionPrompts.SILENCE_SECONDS]
 * without a new phrase, so a last phrase without punctuation is not left waiting for a sequel that never comes. A
 * question that goes on after it was answered ("update") is answered again, as a whole, in the row it already has.
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
        /** Room for the short answer and a few lines of code after it. */
        const val REPLY_TOKENS = 800
        const val REQUEST_TIMEOUT_MILLIS = 60_000L
        /**
         * A question goes on right after the phrase it was answered on, while the other side is still asking: only
         * within this time is the last answer offered to the model as replaceable. A phrase heard later comes after the
         * user had a chance to answer, so whatever the reply says, it gets a row of its own.
         */
        const val CONTINUATION_MILLIS = 6_000L
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
    /** Set by [answerNow]: the next request asks for an answer, and is taken by it. */
    private var answerNowWanted = false
    /** The row on the Questions screen that shows the last entry of [answered]: an "update" reply rewrites it. */
    private var lastRow: Int? = null
    /** When the newest phrase was heard, and when the newest phrase the last answer was given on was heard. */
    private var lastHeardAt = 0L
    private var lastAnsweredAt = 0L

    /**
     * [turn] has its text now; [currentSummary] is the conversation summary as it stands. Phrases are kept even with
     * the mode off, for [answerNow]; only with it on is each one checked for a question.
     */
    fun phraseHeard(turn: ConversationTurn, currentSummary: String) {
        if (turn.source.isBlank()) return
        val checked = preferences.getAppInfo().questions.enabled
        val send = synchronized(lock) {
            // A phrase Whisper wrote down that a voice model then heard again: its text is updated in place.
            if (heard.any { it === turn }) return
            heard.addLast(turn)
            while (heard.size > KEPT_PHRASES) heard.removeFirst()
            summary = currentSummary
            phrasesHeard += 1
            lastHeardAt = System.nanoTime() / 1_000_000
            if (!checked) return
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

    /**
     * The user says a question went by unanswered: the latest phrases are sent asking for an answer to the latest
     * question in them not answered yet, whatever the model made of them before. While a request is out, it goes
     * right after it.
     */
    fun answerNow() {
        val send = synchronized(lock) {
            if (heard.none { it.source.isNotEmpty() }) {
                log.info("Questions: nothing heard yet to answer")
                return
            }
            answerNowWanted = true
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
            lastRow = null
            summary = ""
            epoch += 1
            pending = false
            answerNowWanted = false
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
            // After a clear, pending means phrases heard since it: they are checked like any others. The silence
            // check drops itself when the epoch has moved on.
            val again = synchronized(lock) {
                if (pending) {
                    pending = false
                    true
                } else {
                    inFlight = false
                    false
                }
            }
            if (again) launchRequest(afterSilence = false)
            else if (status == "wait" && !afterSilence) scheduleSilenceCheck(mine)
        }
    }

    private fun scheduleSilenceCheck(mine: Long) {
        // Counted from the newest phrase, as the request then says, not from the reply that asked to wait.
        val (heardThen, heardAt) = synchronized(lock) { phrasesHeard to lastHeardAt }
        val job = backgroundThreadScope.launch {
            delay((heardAt + QuestionPrompts.SILENCE_SECONDS * 1000L - System.nanoTime() / 1_000_000).coerceAtLeast(0L))
            val send = synchronized(lock) {
                if (epoch != mine || phrasesHeard != heardThen || inFlight) false else { inFlight = true; true }
            }
            if (send) launchRequest(afterSilence = true)
        }
        synchronized(lock) { silenceCheck = job }
    }

    /** One request; returns the reply's status, or null when none was sent. */
    private suspend fun ask(afterSilence: Boolean, mine: Long): String? {
        // Taken first: a request that goes no further must not leave the mark for the next ordinary phrase.
        val answerNow = synchronized(lock) { answerNowWanted.also { answerNowWanted = false } }
        val appInfo = preferences.getAppInfo()
        val settings = appInfo.questions
        val model = resolveLlmModel(settings.modelId)?.takeIf { it.readsText }
        if (model == null) {
            log.warn("Questions: the chosen model {} cannot answer text", settings.modelId)
            return null
        }
        val key = when (model.engine) {
            LlmModelEngine.GOOGLE -> appInfo.googleCloudToken
            LlmModelEngine.OPENAI -> appInfo.openAiToken
            LlmModelEngine.CLAUDE -> appInfo.claudeToken
        }
        if (key.isBlank()) {
            log.warn("Questions: no {} API key for {}", model.engine.type, model.title)
            return null
        }
        var newestAt = 0L
        var replaceable = false
        val (speech, history, summaryNow) = synchronized(lock) {
            newestAt = lastHeardAt
            replaceable = lastRow != null && answered.isNotEmpty() && newestAt - lastAnsweredAt <= CONTINUATION_MILLIS
            Triple(
                heard.filter { it.source.isNotEmpty() }.takeLast(settings.phrases.coerceIn(1, KEPT_PHRASES)),
                answered.mapIndexed { i, entry -> if (i >= answered.size - settings.fullAnswers) entry else entry.copy(answer = null) },
                summary,
            )
        }
        if (speech.isEmpty()) return null
        // Asked for by hand, the answer gets a row of its own.
        if (answerNow) replaceable = false
        // The input device does not say whether the user is heard too; speaker labels (Speechmatics) mean several voices.
        val userHeard = speech.any { it.speaker != null }
        val prompt = QuestionPrompts.request(settings, summaryNow, history, speech, afterSilence, replaceable, answerNow, userHeard)
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
        // An "update" rewrites the row of the question answered last; until it is complete, this is what that row said.
        var replaced: QuestionPrompts.Answered? = null
        var last = ""
        try {
            withTimeout(REQUEST_TIMEOUT_MILLIS) {
                stream(model, key, prompt, appInfo.modelParameters[model.id]).collect { text ->
                    last = text
                    if (synchronized(lock) { epoch != mine }) return@collect
                    val reply = QuestionPrompts.parse(text)
                    if ((reply.status != "answer" && reply.status != "update") || !reply.questionComplete || repeated) return@collect
                    val question = reply.question.orEmpty()
                    val answer = reply.answer.orEmpty().ifBlank { "..." }
                    val current = row
                    if (current != null) {
                        display.update(current, question, answer)
                        return@collect
                    }
                    val previous = if (reply.status == "update" && replaceable) {
                        synchronized(lock) { lastRow?.takeIf { answered.isNotEmpty() }?.let { it to answered.last() } }
                    } else {
                        null
                    }
                    if (previous == null && isRepeat(question)) {
                        repeated = true
                        log.info("Questions: \"{}\" was answered already, not shown again", question)
                        return@collect
                    }
                    if (previous != null) {
                        replaced = previous.second
                        row = previous.first
                        display.update(previous.first, question, answer)
                    } else {
                        row = display.add(question, answer)
                    }
                    shown = question
                }
            }
        } catch (e: Throwable) {
            restore(row, replaced, mine)
            throw e
        }
        val reply = QuestionPrompts.parse(last)
        log.debug("Questions reply: {}", reply.status)
        val question = shown
        val answer = reply.answer.orEmpty()
        val current = row
        if (current != null && question != null && answer.isNotBlank() &&
            remember(QuestionPrompts.Answered(question, answer), current, replaced != null, newestAt, mine)
        ) {
            display.update(current, question, answer)
            display.settled(current)
        } else {
            restore(current, replaced, mine)
        }
        return reply.status
    }

    /**
     * Keeps [entry], shown in [row], as the last answer (in place of the last one when [replacing]), unless Clear dialog
     * came since the request was sent: checked in the same lock as the writes, so a cleared history gets nothing old.
     */
    private fun remember(entry: QuestionPrompts.Answered, row: Int, replacing: Boolean, heardAt: Long, mine: Long): Boolean =
        synchronized(lock) {
            if (epoch != mine) return false
            if (replacing && answered.isNotEmpty()) answered[answered.lastIndex] = entry else answered += entry
            while (answered.size > MAX_HISTORY) answered.removeAt(0)
            lastRow = row
            lastAnsweredAt = heardAt
            true
        }

    /** An update that did not finish leaves its row as it was before: the answer it was replacing. */
    private fun restore(row: Int?, replaced: QuestionPrompts.Answered?, mine: Long) {
        if (row == null || replaced == null || synchronized(lock) { epoch != mine }) return
        display.update(row, replaced.question, replaced.answer.orEmpty())
    }

    private fun isRepeat(question: String): Boolean = synchronized(lock) {
        answered.any { QuestionPrompts.overlap(it.question, question) >= QuestionPrompts.SAME_QUESTION_OVERLAP }
    }

    /**
     * The chosen model's settings from the LLM page, except that questions run at temperature 0 with room for
     * [REPLY_TOKENS] unless the page sets them: the design was tested at temperature 0.
     */
    private fun stream(model: LlmModel, key: String, prompt: ModelPrompt, chosen: ModelParameters?): Flow<String> {
        val own = (chosen ?: ModelParameters()).let {
            it.copy(temperature = it.temperature ?: 0.0, maxOutputTokens = it.maxOutputTokens ?: REPLY_TOKENS)
        }
        return cloud.streamText(model, key, prompt, effectiveParameters(model.parameterSupport, own))
    }
}
