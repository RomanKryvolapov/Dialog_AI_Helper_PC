package utils

import RECENT_TURNS_MAX_CHARS
import models.domain.ConversationTurn

/**
 * The conversation as the translation prompt sees it: the newest turns verbatim, with the
 * translations already given, plus a running summary of everything older.
 *
 * Only the turns not yet folded into the summary are kept, and a prompt sees all of them. Once they
 * render to more than [FOLD_AT_CHARS], everything except the newest turns that fit
 * [RECENT_TURNS_MAX_CHARS] is handed out as a [Fold] — one summary request, never for fewer than
 * [MIN_FOLD_TURNS] turns — and [foldDone] swaps those turns for the new summary. So a prompt carries
 * the summary plus one to two budgets of verbatim turns, and the summary is refreshed about once per
 * budget of new speech, or every few phrases when phrases are long. After a failed request folding
 * resumes [RETRY_AFTER_TURNS] phrases later, however much piled up while the request was out.
 *
 * Thread-safe: phrases arrive on the FX thread, translations and summaries on background threads.
 */
class ConversationContext {

    companion object {
        /** Unfolded turns rendering to more than this are folded into the summary. */
        const val FOLD_AT_CHARS = 2 * RECENT_TURNS_MAX_CHARS

        /** Unfolded turns are capped here; reached only while a summary stalls or after one failed. */
        const val MAX_KEPT_CHARS = 4 * RECENT_TURNS_MAX_CHARS

        /** A summary request folds at least this many turns, so long phrases do not cost one each. */
        const val MIN_FOLD_TURNS = 3

        /** After a failed summary, folding is tried again this many phrases later. */
        const val RETRY_AFTER_TURNS = 5

        // The previous-sentence prompts read up to five turns back, whatever their length.
        private const val PREVIOUS_SENTENCE_TURNS = 5

        /**
         * One turn in the shape the conversation prompts (PROMPT_TRANSLATE_CONVERSATION,
         * PROMPT_TRANSLATE_VOICE, PROMPT_CONVERSATION_SUMMARY) describe: what was said and, once ready,
         * its translation. A voice turn whose transcript has not arrived yet renders as nothing.
         */
        fun render(turn: ConversationTurn): String {
            val source = turn.source
            if (source.isEmpty()) return ""
            val translation = turn.translation
            return buildString {
                append("<turn>\n<said>").append(source).append("</said>\n")
                if (translation != null) append("<translated>").append(translation).append("</translated>\n")
                append("</turn>")
            }
        }

        /** [turns] as the conversation prompts take them — {{recent_turns}}, or the turns to summarize. */
        fun renderTurns(turns: List<ConversationTurn>): String =
            turns.map(::render).filter { it.isNotEmpty() }.joinToString("\n")

        /** How many of the newest [turns] fit [maxChars] once rendered — at least [minTurns] of them. */
        private fun newestCount(turns: List<ConversationTurn>, maxChars: Int, minTurns: Int): Int {
            var count = 0
            var used = 0
            for (turn in turns.asReversed()) {
                val length = render(turn).length
                if (count >= minTurns && used + length > maxChars) break
                count += 1
                used += length
            }
            return count
        }
    }

    /** What one prompt sees: the summary and the unfolded turns said before its phrase. */
    class Snapshot(val summary: String, val turns: List<ConversationTurn>)

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

    /**
     * Records a newly recognized phrase. [Added.before] is the context its own prompt gets; [Added.fold]
     * is a summary request to run now, or null. With [summarize] false — the selected prompt uses no
     * summary — nothing is folded and turns that no prompt would see are dropped.
     */
    fun add(source: String, summarize: Boolean): Added = synchronized(lock) {
        val before = Snapshot(summary, turns.toList())
        val turn = ConversationTurn(source)
        turns.add(turn)
        var fold: Fold? = null
        if (!summarize) {
            keepNewest(FOLD_AT_CHARS, PREVIOUS_SENTENCE_TURNS)
        } else {
            if (retryAfterTurns > 0) retryAfterTurns -= 1
            if (!foldInFlight && retryAfterTurns == 0 && renderedChars() > FOLD_AT_CHARS) {
                val keep = newestCount(turns, RECENT_TURNS_MAX_CHARS, minTurns = 1)
                // A voice turn still waiting for its transcript ends the fold: folded empty, it would
                // reach neither the summary nor any later prompt once its text arrived.
                val older = turns.subList(0, turns.size - keep)
                val pending = older.indexOfFirst { it.source.isEmpty() }
                val foldable = if (pending < 0) older else older.subList(0, pending)
                if (foldable.size >= MIN_FOLD_TURNS) {
                    fold = Fold(epoch, summary, foldable.toList())
                    foldInFlight = true
                }
            }
            keepNewest(MAX_KEPT_CHARS, MIN_FOLD_TURNS + 1)
        }
        Added(turn, before, fold)
    }

    /**
     * Takes the result of [fold]'s summary request; [newSummary] null means it failed, and the turns
     * stay verbatim until [RETRY_AFTER_TURNS] more phrases have been said, when folding is tried again.
     */
    fun foldDone(fold: Fold, newSummary: String?): Unit = synchronized(lock) {
        if (fold.epoch != epoch) return
        foldInFlight = false
        if (newSummary.isNullOrBlank()) {
            retryAfterTurns = RETRY_AFTER_TURNS
            return
        }
        summary = newSummary
        val folded = fold.turns.toHashSet()
        turns.removeAll { it in folded }
    }

    /** Forgets [turn] — a voice phrase that turned out to hold no speech, or that was never transcribed. */
    fun discard(turn: ConversationTurn): Unit = synchronized(lock) {
        turns.remove(turn)
    }

    /** A new conversation: forgets every turn and the summary; a summary still in flight is ignored. */
    fun clear(): Unit = synchronized(lock) {
        turns.clear()
        summary = ""
        epoch += 1
        foldInFlight = false
        retryAfterTurns = 0
    }

    private fun renderedChars(): Int = turns.sumOf { render(it).length }

    private fun keepNewest(maxChars: Int, minTurns: Int) {
        val keep = newestCount(turns, maxChars, minTurns)
        if (keep < turns.size) turns.subList(0, turns.size - keep).clear()
    }
}
