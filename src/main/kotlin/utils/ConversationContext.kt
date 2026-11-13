package utils

import models.domain.ConversationTurn
import org.slf4j.LoggerFactory

/**
 * The conversation as the translation requests see it: a running summary of everything older, plus the last
 * few phrases verbatim (the window), each with the translation already given for it.
 *
 * The summary is written by a request of its own, never by a translation: as soon as a phrase leaves the
 * window and there is no summary yet, and after that each time [Settings.summaryEvery] phrases have left it
 * since the last one. Such a request ([Fold]) gets the summary so far and the phrases that left the window,
 * and [foldDone] swaps those phrases for the new summary. So a request always sees the summary plus the
 * window, and while the summary requests keep up, at most [Settings.summaryEvery] phrases between the two -
 * the ones waiting for the next summary - are seen by neither. After a failed summary the next try waits
 * [RETRY_AFTER_TURNS] phrases.
 *
 * Thread-safe: phrases arrive on the FX thread, translations and summaries on background threads.
 */
class ConversationContext {

    companion object {
        /** After a failed summary, the next is tried this many phrases later. */
        const val RETRY_AFTER_TURNS = 5

        /** Phrases waiting for a summary are capped at this many summaries' worth, reached only while they fail. */
        private const val MAX_PENDING_SUMMARIES = 4

        private val log = LoggerFactory.getLogger("ConversationContext")
    }

    /** [window]: earlier phrases each request carries; [summaryEvery]: phrases per summary, when [summarize]. */
    data class Settings(val window: Int, val summarize: Boolean, val summaryEvery: Int)

    /** What one request sees: the summary, and the phrases said before its own. */
    class Snapshot(val summary: String, private val turns: List<ConversationTurn>) {
        /**
         * The last [count] phrases that have text, oldest first. Read when the request is built, so a phrase
         * whose transcript arrived since the snapshot was taken counts, and one still waiting takes no place.
         */
        fun previous(count: Int): List<ConversationTurn> =
            if (count <= 0) emptyList() else turns.filter { it.source.isNotEmpty() }.takeLast(count)
    }

    /** One summary request: fold [turns] into [summary]. */
    class Fold(internal val epoch: Long, val summary: String, val turns: List<ConversationTurn>)

    class Added(val turn: ConversationTurn, val before: Snapshot, val fold: Fold?)

    private val lock = Any()
    private val turns = ArrayList<ConversationTurn>()
    private var summary = ""
    private var epoch = 0L
    private var foldInFlight = false

    // After a failed summary: how many more phrases to wait for before trying again.
    private var retryAfterTurns = 0

    /** The summary as it stands. */
    val currentSummary: String get() = synchronized(lock) { summary }

    /**
     * Records a newly recognized phrase ([source] empty while its text is not known yet). [Added.before] is
     * the context its own request gets; [Added.fold] is a summary request to send now, or null.
     */
    fun add(source: String, settings: Settings): Added = synchronized(lock) {
        val before = Snapshot(summary, turns.toList())
        val turn = ConversationTurn(source)
        turns.add(turn)
        var fold: Fold? = null
        if (!settings.summarize) {
            turns.retainAll(inWindow(settings.window) + turns.filter { it.source.isEmpty() })
        } else {
            if (retryAfterTurns > 0) retryAfterTurns -= 1
            val waiting = waitingForSummary(settings.window)
            val due = waiting.size >= settings.summaryEvery.coerceAtLeast(1) || (summary.isEmpty() && waiting.isNotEmpty())
            if (!foldInFlight && retryAfterTurns == 0 && due) {
                fold = Fold(epoch, summary, waiting)
                foldInFlight = true
            }
            val cap = MAX_PENDING_SUMMARIES * settings.summaryEvery.coerceAtLeast(1)
            if (waiting.size > cap && fold == null) {
                val dropped = waiting.take(waiting.size - cap)
                log.warn("{} phrases dropped from the conversation context: no summary could take them in", dropped.size)
                turns.removeAll(dropped.toSet())
            }
        }
        Added(turn, before, fold)
    }

    /**
     * Takes the result of [fold]'s summary request; [newSummary] null means it failed, and its phrases wait
     * for the next try, [RETRY_AFTER_TURNS] phrases later.
     */
    fun foldDone(fold: Fold, newSummary: String?): Unit = synchronized(lock) {
        if (fold.epoch != epoch) return
        foldInFlight = false
        if (newSummary.isNullOrBlank()) {
            retryAfterTurns = RETRY_AFTER_TURNS
            return
        }
        summary = newSummary
        turns.removeAll(fold.turns.toSet())
    }

    /** Forgets [turn] - a phrase that turned out to hold no speech, or that was never transcribed. */
    fun discard(turn: ConversationTurn): Unit = synchronized(lock) {
        turns.remove(turn)
    }

    /** A new conversation: forgets every phrase and the summary; a summary still in flight is ignored. */
    fun clear(): Unit = synchronized(lock) {
        turns.clear()
        summary = ""
        epoch += 1
        foldInFlight = false
        retryAfterTurns = 0
    }

    /** The newest [window] phrases that have text. */
    private fun inWindow(window: Int): List<ConversationTurn> =
        if (window <= 0) emptyList() else turns.filter { it.source.isNotEmpty() }.takeLast(window)

    /**
     * The phrases that left the window and no summary has taken in yet, oldest first. They stop at a phrase
     * still waiting for its text: folded before it, a later one would reach the summary out of order.
     */
    private fun waitingForSummary(window: Int): List<ConversationTurn> {
        val windowed = inWindow(window).toSet()
        return turns.takeWhile { it.source.isNotEmpty() }.filter { it !in windowed }
    }
}
