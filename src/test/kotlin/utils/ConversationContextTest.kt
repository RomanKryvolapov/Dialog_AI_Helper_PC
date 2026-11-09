package utils

import RECENT_TURNS_MAX_CHARS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ConversationContextTest {

    /** A phrase whose rendered turn, translated, is about 200 characters. */
    private fun phrase(i: Int) = "phrase number $i of the meeting, long enough to look like a real fragment"

    private fun ConversationContext.say(i: Int, summarize: Boolean = true): ConversationContext.Added =
        add(phrase(i), summarize).also { it.turn.translation = "перевод фразы номер $i, примерно такой же длины, как исходник" }

    private fun renderedChars(snapshot: ConversationContext.Snapshot) =
        snapshot.turns.sumOf { ConversationContext.render(it).length }

    /** Says phrases from [from] on until a summary is requested; fails rather than looping forever. */
    private fun ConversationContext.sayUntilFold(from: Int = 1): Pair<ConversationContext.Added, Int> {
        for (i in from until from + 200) {
            val added = say(i)
            if (added.fold != null) return added to i
        }
        fail("no summary was requested in 200 phrases")
    }

    @Test
    fun `no fold while the unfolded turns fit twice the recent budget`() {
        val context = ConversationContext()
        val (added, i) = context.sayUntilFold()
        val pendingChars = renderedChars(added.before) + ConversationContext.render(added.turn).length
        assertTrue(pendingChars > ConversationContext.FOLD_AT_CHARS, "folded before twice the budget was pending")
        assertTrue(i > 5, "folded after only $i phrases")
    }

    @Test
    fun `a fold keeps the newest budget verbatim and the next prompt sees the summary instead`() {
        val context = ConversationContext()
        val (first, foldedAt) = context.sayUntilFold()
        val fold = first.fold!!
        var i = foldedAt

        val folded = fold.turns.map { it.source }
        assertEquals(phrase(1), folded.first(), "the oldest turn is folded first")
        assertTrue(folded.size < i, "every turn was folded, none kept verbatim")
        assertEquals("", fold.summary)

        // While the request is in flight no second fold is handed out.
        assertNull(context.say(++i).fold)

        context.foldDone(fold, "notes: release post-mortem, «ты»")
        val next = context.say(++i).before
        assertEquals("notes: release post-mortem, «ты»", next.summary)
        assertTrue(next.turns.none { it.source in folded }, "folded turns are still sent verbatim")
        assertTrue(renderedChars(next) <= ConversationContext.FOLD_AT_CHARS)
        assertTrue(next.turns.size >= 5)
        assertEquals(
            next.turns.joinToString("\n") { ConversationContext.render(it) },
            ConversationContext.renderTurns(next.turns),
        )
    }

    @Test
    fun `every earlier phrase is either summarized or sent verbatim`() {
        for (words in listOf(1, 18)) { // short phrases, and phrases of 15 seconds or more
            val context = ConversationContext()
            val summarized = HashSet<String>()
            fun source(k: Int) = "phrase $k " + "and some more words ".repeat(words)
            repeat(60) { k ->
                val added = context.add(source(k), summarize = true)
                val visible = added.before.turns.map { it.source }.toSet()
                for (j in 0 until k) {
                    assertTrue(source(j) in summarized || source(j) in visible, "phrase $j lost before phrase $k ($words words)")
                }
                added.turn.translation = "перевод ".repeat(words * 3 + 4)
                added.fold?.let { fold ->
                    summarized += fold.turns.map { it.source }
                    context.foldDone(fold, "notes after phrase $k")
                }
            }
            assertTrue(summarized.isNotEmpty(), "nothing was ever summarized ($words words)")
        }
    }

    @Test
    fun `a failed fold keeps the turns and retries a fixed number of phrases later`() {
        val context = ConversationContext()
        val (first, foldedAt) = context.sayUntilFold()
        val fold = first.fold!!
        var i = foldedAt
        context.foldDone(fold, null)

        var phrasesAfterFailure = 0
        var retry: ConversationContext.Fold? = null
        while (retry == null && phrasesAfterFailure < 50) {
            val added = context.say(++i)
            phrasesAfterFailure++
            if (phrasesAfterFailure == 1) {
                assertTrue(added.before.turns.any { it.source == phrase(1) }, "a failed summary lost the oldest turns")
                assertEquals("", added.before.summary)
            }
            retry = added.fold
        }
        assertEquals(ConversationContext.RETRY_AFTER_TURNS, phrasesAfterFailure)
    }

    @Test
    fun `a summary that fails late still lets folding resume`() {
        val context = ConversationContext()
        val (first, foldedAt) = context.sayUntilFold()
        val fold = first.fold!!
        var i = foldedAt
        // The request stays out (a long quota wait, say) while much more is said and translated.
        repeat(30) { assertNull(context.say(++i).fold, "a second summary was handed out while one is in flight") }
        context.foldDone(fold, null)

        var after = 0
        var retry: ConversationContext.Fold? = null
        while (retry == null && after < 50) {
            retry = context.say(++i).fold
            after++
        }
        assertNotNull(retry, "folding never resumed after a late failure")
        assertEquals(ConversationContext.RETRY_AFTER_TURNS, after)
    }

    @Test
    fun `long phrases fold several turns at a time, not one per phrase`() {
        val context = ConversationContext()
        val long = "a long phrase that keeps going on and on ".repeat(18)
        var folds = 0
        repeat(40) { k ->
            val added = context.add(long + k, summarize = true)
            added.turn.translation = "длинный перевод ".repeat(45)
            added.fold?.let {
                folds++
                assertTrue(it.turns.size >= ConversationContext.MIN_FOLD_TURNS, "folded only ${it.turns.size} turns")
                context.foldDone(it, "notes after phrase $k")
            }
        }
        assertTrue(folds in 1..14, "$folds summary requests for 40 long phrases")
    }

    @Test
    fun `clear starts a new conversation and ignores a summary still in flight`() {
        val context = ConversationContext()
        val (first, foldedAt) = context.sayUntilFold()
        val fold = first.fold!!
        var i = foldedAt

        context.clear()
        context.foldDone(fold, "notes from the previous conversation")
        val next = context.say(++i).before
        assertEquals("", next.summary)
        assertTrue(next.turns.isEmpty())
    }

    @Test
    fun `without a summary prompt nothing is folded and old turns are dropped`() {
        val context = ConversationContext()
        val longestTurn = ConversationContext.render(context.say(1, summarize = false).turn).length
        repeat(60) { k ->
            val added = context.say(k + 2, summarize = false)
            assertNull(added.fold)
            // Trimmed when a phrase is added, before its translation arrives: at most that one turn
            // more than the budget is kept.
            assertTrue(renderedChars(added.before) <= ConversationContext.FOLD_AT_CHARS + longestTurn)
            assertTrue(added.before.turns.size >= minOf(5, k + 1), "fewer than five earlier turns kept")
        }
        val last = context.say(62, summarize = false).before
        assertEquals(phrase(61), last.turns.last().source)
    }

    @Test
    fun `the newest five turns are kept even when they are long`() {
        val context = ConversationContext()
        val long = "x".repeat(RECENT_TURNS_MAX_CHARS)
        repeat(8) { context.add(long + it, summarize = false) }
        val before = context.add("short", summarize = false).before
        assertEquals(5, before.turns.size)
        assertEquals(long + 7, before.turns.last().source)
    }

    @Test
    fun `a turn renders its translation only once it is ready`() {
        val turn = ConversationContext().add("hey Shivon got a minute", summarize = true).turn
        assertEquals("<turn>\n<said>hey Shivon got a minute</said>\n</turn>", ConversationContext.render(turn))
        turn.translation = "Шивон, есть минутка?"
        assertEquals(
            "<turn>\n<said>hey Shivon got a minute</said>\n<translated>Шивон, есть минутка?</translated>\n</turn>",
            ConversationContext.render(turn),
        )
    }

    @Test
    fun `a voice phrase still waiting for its transcript is never folded away`() {
        val context = ConversationContext()
        repeat(4) { context.say(it + 1) }
        val waiting = context.add("", summarize = true).turn // sent, transcript not back yet
        var i = 5
        var fold: ConversationContext.Fold? = null
        while (fold == null && i < 200) fold = context.say(++i).fold
        val folded = assertNotNull(fold, "nothing was folded").turns
        assertTrue(waiting !in folded, "the waiting voice turn was folded while empty")
        assertTrue(folded.all { it.source.isNotEmpty() })

        // Once its transcript is in, it can be folded like any other turn.
        waiting.source = "the transcript that arrived late"
        waiting.translation = "расшифровка, пришедшая позже"
        context.foldDone(fold, "notes")
        var later: ConversationContext.Fold? = null
        while (later == null && i < 400) later = context.say(++i).fold
        assertTrue(waiting in assertNotNull(later).turns, "the transcribed voice turn was never folded")
    }

    @Test
    fun `a turn without text takes no previous-sentence slot`() {
        val context = ConversationContext()
        context.add("we start the meeting now", summarize = false)
        val noise = context.add("", summarize = false).turn // a chunk still with Whisper, later heard as silence
        val before = context.add("the release slipped", summarize = false).before
        context.discard(noise)
        assertEquals("we start the meeting now", ConversationContext.previousText(before.turns, 1))
        assertEquals("", ConversationContext.previousText(before.turns, 2))
        noise.source = "late transcript"
        assertEquals("late transcript", ConversationContext.previousText(before.turns, 1), "once it has text it counts")
    }

    @Test
    fun `a voice phrase joins the conversation once its transcript arrives, and silence is discarded`() {
        val context = ConversationContext()
        context.say(1)
        val voice = context.add("", summarize = true).turn
        val silent = context.add("", summarize = true).turn

        // Still being transcribed: the next prompt sees only the phrase that has text.
        var next = context.add("", summarize = true).before
        assertEquals(ConversationContext.render(next.turns.first()), ConversationContext.renderTurns(next.turns))

        voice.source = "honestly I haven't"
        voice.translation = "честно говоря, не спал"
        context.discard(silent)
        next = context.add("", summarize = true).before
        assertTrue(silent !in next.turns, "a silent phrase stayed in the conversation")
        assertTrue("<said>honestly I haven't</said>" in ConversationContext.renderTurns(next.turns))
    }
}
