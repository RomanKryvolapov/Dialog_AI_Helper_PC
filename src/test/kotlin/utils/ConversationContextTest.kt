package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationContextTest {

    private val settings = ConversationContext.Settings(window = 3, summarize = true, summaryEvery = 6)

    private fun phrase(i: Int) = "phrase $i"

    private fun ConversationContext.say(i: Int, with: ConversationContext.Settings = settings): ConversationContext.Added =
        add(phrase(i), with).also { it.turn.translation = "фраза $i" }

    private fun ConversationContext.Snapshot.previousSources(count: Int) = previous(count).map { it.source }

    @Test
    fun `the first summary is asked for as soon as a phrase leaves the window`() {
        val context = ConversationContext()
        (1..3).forEach { assertNull(context.say(it).fold, "a summary before any phrase left the window") }
        val fold = assertNotNull(context.say(4).fold)
        assertEquals("", fold.summary)
        assertEquals(listOf(phrase(1)), fold.turns.map { it.source })
    }

    @Test
    fun `after that a summary is asked for each time the set number of phrases has left the window`() {
        val context = ConversationContext()
        (1..3).forEach { context.say(it) }
        context.foldDone(assertNotNull(context.say(4).fold), "s1")
        var i = 4
        while (true) {
            val fold = context.say(++i).fold ?: continue
            assertEquals(10, i, "phrases 2 to 7 left the window by phrase 10")
            assertEquals("s1", fold.summary)
            assertEquals((2..7).map(::phrase), fold.turns.map { it.source })
            break
        }
    }

    @Test
    fun `a request gets the summary and the last phrases, and nothing it sees twice`() {
        val context = ConversationContext()
        (1..3).forEach { context.say(it) }
        val first = assertNotNull(context.say(4).fold)
        context.foldDone(first, "summary of phrase 1")
        val before = context.say(5).before
        assertEquals("summary of phrase 1", before.summary)
        assertEquals(listOf(phrase(2), phrase(3), phrase(4)), before.previousSources(3))
        assertTrue(before.previous(10).none { it in first.turns }, "a summarized phrase is still sent verbatim")
    }

    @Test
    fun `no phrase is lost to the summary and the window together beyond the set number`() {
        val context = ConversationContext()
        val summarized = HashSet<String>()
        for (i in 1..120) {
            val added = context.say(i)
            val seen = summarized + added.before.previousSources(settings.window)
            val unseen = (1 until i).map(::phrase).filter { it !in seen }
            assertTrue(unseen.size <= settings.summaryEvery, "phrase $i: ${unseen.size} earlier phrases reach neither")
            added.fold?.let { fold ->
                summarized += fold.turns.map { it.source }
                context.foldDone(fold, "summary up to $i")
            }
        }
    }

    @Test
    fun `while a summary request is out no second one is handed out`() {
        val context = ConversationContext()
        (1..3).forEach { context.say(it) }
        assertNotNull(context.say(4).fold)
        (5..30).forEach { assertNull(context.say(it).fold, "a second summary while the first was out") }
    }

    @Test
    fun `a failed summary keeps its phrases and is tried again a fixed number of phrases later`() {
        val context = ConversationContext()
        (1..3).forEach { context.say(it) }
        val failed = assertNotNull(context.say(4).fold)
        context.foldDone(failed, null)
        repeat(ConversationContext.RETRY_AFTER_TURNS - 1) { assertNull(context.say(5 + it).fold) }
        val retry = assertNotNull(context.say(4 + ConversationContext.RETRY_AFTER_TURNS).fold)
        assertEquals(phrase(1), retry.turns.first().source, "the phrase of the failed summary was dropped")
        assertEquals("", retry.summary)
    }

    @Test
    fun `clear dialog starts over and a summary still in flight is ignored`() {
        val context = ConversationContext()
        (1..3).forEach { context.say(it) }
        val fold = assertNotNull(context.say(4).fold)
        context.clear()
        context.foldDone(fold, "from the old conversation")
        assertEquals("", context.currentSummary)
        val before = context.say(1).before
        assertEquals("", before.summary)
        assertEquals(emptyList(), before.previous(3))
    }

    @Test
    fun `without a summary only the window is kept`() {
        val off = settings.copy(summarize = false)
        val context = ConversationContext()
        (1..20).forEach { assertNull(context.say(it, off).fold) }
        val before = context.say(21, off).before
        assertEquals(listOf(phrase(18), phrase(19), phrase(20)), before.previousSources(10))
        assertEquals("", before.summary)
    }

    @Test
    fun `a phrase still waiting for its text takes no window place and holds back the summary after it`() {
        val context = ConversationContext()
        context.say(1)
        val waiting = context.add("", settings).turn // a Whisper row whose text has not come back yet
        (2..12).forEach { i ->
            context.say(i).fold?.let { fold ->
                assertEquals(listOf(phrase(1)), fold.turns.map { it.source }, "folded past the phrase still waiting")
                context.foldDone(fold, "s")
            }
        }
        assertEquals(listOf(phrase(10), phrase(11), phrase(12)), context.say(13).before.previousSources(3))

        waiting.source = "the late one"
        waiting.translation = "запоздавшая"
        var fold: ConversationContext.Fold? = null
        var i = 13
        while (fold == null && i < 40) fold = context.say(++i).fold
        assertEquals("the late one", assertNotNull(fold).turns.first().source, "the late phrase reached the summary out of order")
    }

    @Test
    fun `a voice phrase joins the conversation once its transcript arrives, and silence is discarded`() {
        val context = ConversationContext()
        context.say(1)
        val voice = context.add("", settings).turn
        val silent = context.add("", settings).turn
        assertEquals(listOf(phrase(1)), context.add("", settings).before.previousSources(3))

        voice.source = "honestly I haven't"
        voice.translation = "честно говоря, не спал"
        context.discard(silent)
        val before = context.add("", settings).before
        assertEquals(listOf(phrase(1), "honestly I haven't"), before.previousSources(3))
    }

    @Test
    fun `phrases waiting for a summary that keeps failing are capped`() {
        val context = ConversationContext()
        for (i in 1..200) {
            context.say(i).fold?.let { context.foldDone(it, null) }
        }
        val fold = assertNotNull(generateSequence(201) { it + 1 }.take(10).firstNotNullOfOrNull { context.say(it).fold })
        assertTrue(fold.turns.size <= 4 * settings.summaryEvery + 1, "${fold.turns.size} phrases kept waiting")
    }
}
