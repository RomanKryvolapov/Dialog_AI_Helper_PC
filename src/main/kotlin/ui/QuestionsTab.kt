package ui

import backgroundThreadScope
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.scene.control.Tooltip
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.util.Duration
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.Border
import javafx.scene.layout.BorderStroke
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.layout.BorderWidths
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import utils.AnswerExpander
import utils.CodeHighlighter
import utils.CodeTokenKind
import utils.QuestionsDisplay
import utils.classifyFailure
import utils.splitCodeBlocks

/**
 * The Questions screen: every question the assistant found in the conversation, restated in full, with its answer,
 * newest at the bottom. A click on an answer opens, under it, the follow-up questions the other side may ask next
 * (asked for as soon as the answer is complete); picking one writes a fuller answer under the short one. ✕ takes a
 * card off the screen: it stays in the list, hidden, since the Questions mode addresses rows by their place.
 */
class QuestionsTab(private val expander: AnswerExpander) : QuestionsDisplay {

    /** The newest card's question and answer as they stand; [serial] grows with each new card. */
    data class Latest(val serial: Long, val row: Int, val question: String, val answer: String)

    private companion object {
        const val BACKGROUND_COLOUR = "#323232"
        const val TEXT_COLOUR = "#FFFFFF"
        const val MUTED_COLOUR = "#999999"
        const val LINK_COLOUR = "#8AB8EE"
        const val CODE_BACKGROUND = "#0A0C10"
        /** A dark editor palette on [CODE_BACKGROUND]. */
        val CODE_COLOURS = mapOf(
            CodeTokenKind.PLAIN to "#D4D4D4",
            CodeTokenKind.KEYWORD to "#569CD6",
            CodeTokenKind.STRING to "#CE9178",
            CodeTokenKind.COMMENT to "#6A9955",
            CodeTokenKind.NUMBER to "#B5CEA8",
            CodeTokenKind.FUNCTION to "#DCDCAA",
        )
        /** As the Messages screen's phrases (`original-message-label` in the stylesheet). */
        const val ANSWER_FONT_SIZE = 18.0
        const val CODE_FONT_SIZE = 16.0
        /** Thicker than the default monospaced face (Courier), which is hard to read; JavaFX falls back where missing. */
        const val CODE_FONT = "Consolas"
        /** Terms in `backticks` inside an answer. */
        const val TERM_COLOUR = "#9CDCFE"
    }

    private sealed interface FollowUps {
        data object NotAsked : FollowUps
        data object Loading : FollowUps
        data class Ready(val questions: List<String>) : FollowUps
        data class Failed(val reason: String) : FollowUps
    }

    private class Expansion(val followUp: String, var text: String = "", var done: Boolean = false, var failure: String? = null)

    /** One card; touched on the FX thread only. */
    private class Row(var question: String, var answer: String) {
        var followUps: FollowUps = FollowUps.NotAsked
        /** The answer text [followUps] were asked for: one that changed since gets them asked for again. */
        var followUpsFor: String? = null
        val expansions = ArrayList<Expansion>()
        var open = false
        var dismissed = false
    }

    private val log = LoggerFactory.getLogger("QuestionsTab")

    private val cards = VBox(4.0).apply {
        padding = Insets(4.0)
        background = Background(BackgroundFill(Color.web(BACKGROUND_COLOUR), null, null))
    }

    private val scrollPane = ScrollPane(cards).apply {
        isFitToWidth = true
        style = "-fx-background: $BACKGROUND_COLOUR; -fx-background-color: $BACKGROUND_COLOUR;"
    }

    val content: ScrollPane get() = scrollPane

    private val entryBackground = Background(BackgroundFill(Color.web("#2f2f2f"), CornerRadii(8.0), Insets.EMPTY))
    /** Darker than the card, so the question stands apart from the answer under it. */
    private val questionBackground = Background(BackgroundFill(Color.web("#1F1F1F"), CornerRadii(6.0), Insets.EMPTY))
    private val entryBorder = Border(BorderStroke(Color.web("#444444"), BorderStrokeStyle.SOLID, CornerRadii(8.0), BorderWidths(1.0)))

    // Rows handed out; counted here, not read from the list, which the FX thread fills later in the same order.
    private var count = 0
    private val rows = ArrayList<Row>()
    /** Bumped by [clear]: a reply to a request from before it changes nothing. */
    private var epoch = 0
    private val jobs = ArrayList<Job>()
    private var serial = 0L

    private val latestFlow = MutableStateFlow<Latest?>(null)
    /** For the floating panel and the tray icon's new-answer mark; null while the screen is empty. Set on the FX thread. */
    val latest: StateFlow<Latest?> = latestFlow

    /** Adds a found question with its answer so far; the returned row is passed back to [update] as the answer grows. */
    override fun add(question: String, answer: String): Int {
        val index = synchronized(this) { count++ }
        Platform.runLater {
            val atBottom = isScrolledToBottom()
            val row = Row(question, answer)
            rows.add(row)
            cards.children.add(card(index, row))
            if (atBottom) scrollToBottom()
            latestFlow.value = Latest(++serial, index, question, answer)
        }
        return index
    }

    /** The question asked again in full: what was asked for the old wording no longer fits, so it goes. */
    override fun update(row: Int, question: String, answer: String) {
        Platform.runLater {
            val state = rows.getOrNull(row) ?: return@runLater
            if (state.question != question) {
                state.followUps = FollowUps.NotAsked
                state.followUpsFor = null
                state.expansions.clear()
                state.open = false
            }
            state.question = question
            state.answer = answer
            redraw(row)
            latestFlow.value?.takeIf { it.row == row }?.let { latestFlow.value = it.copy(question = question, answer = answer) }
        }
    }

    /** The follow-ups are asked for as soon as an answer is complete, so its list opens with them ready. */
    override fun settled(row: Int) {
        Platform.runLater { askFollowUps(row) }
    }

    override fun clear() {
        synchronized(this) { count = 0 }
        Platform.runLater {
            epoch += 1
            jobs.forEach { it.cancel() }
            jobs.clear()
            rows.clear()
            cards.children.clear()
            latestFlow.value = null
        }
    }

    private fun askFollowUps(index: Int) {
        val row = rows.getOrNull(index) ?: return
        if (row.followUps is FollowUps.Loading || (row.followUps is FollowUps.Ready && row.followUpsFor == row.answer)) return
        row.followUps = FollowUps.Loading
        row.followUpsFor = row.answer
        redraw(index)
        val (question, answer, mine) = Triple(row.question, row.answer, epoch)
        launch {
            val result = try {
                FollowUps.Ready(expander.followUps(question, answer))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.warn("Follow-up questions not made: {}", reason(e))
                FollowUps.Failed(reason(e))
            }
            Platform.runLater {
                if (epoch == mine && rows.getOrNull(index) === row && row.question == question && row.followUpsFor == answer) {
                    row.followUps = result
                    redraw(index)
                    // The answer grew while they were made: they are asked for again for the one now shown.
                    if (row.answer != answer) {
                        row.followUps = FollowUps.NotAsked
                        askFollowUps(index)
                    }
                }
            }
        }
    }

    private fun expand(index: Int, followUp: String) {
        val row = rows.getOrNull(index) ?: return
        val expansion = Expansion(followUp)
        row.expansions.add(expansion)
        row.open = false
        redraw(index)
        val (question, answer, mine) = Triple(row.question, row.answer, epoch)
        fun write(change: () -> Unit) = Platform.runLater {
            if (epoch == mine && rows.getOrNull(index) === row && expansion in row.expansions) {
                change()
                redraw(index)
            }
        }
        launch {
            try {
                expander.expand(question, answer, followUp).collect { text -> write { expansion.text = text } }
                write {
                    expansion.done = true
                    if (expansion.text.isBlank()) expansion.failure = "the model wrote nothing"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log.warn("Fuller answer not written: {}", reason(e))
                write {
                    expansion.done = true
                    expansion.failure = reason(e)
                }
            }
        }
    }

    private fun launch(work: suspend () -> Unit) {
        jobs.removeAll { !it.isActive }
        jobs += backgroundThreadScope.launch { work() }
    }

    private fun reason(e: Throwable): String =
        if (e is IllegalStateException) e.message.orEmpty() else classifyFailure(e).kind.reason

    private fun redraw(index: Int) {
        val row = rows.getOrNull(index) ?: return
        if (index !in 0 until cards.children.size) return
        val atBottom = isScrolledToBottom()
        cards.children[index] = card(index, row)
        if (atBottom) scrollToBottom()
    }

    private fun card(index: Int, row: Row): VBox {
        val question = Label(row.question).apply {
            styleClass.add("original-message-label")
            // Lighter and a little larger than on Messages, where the same style marks the original phrase.
            style = "-fx-text-fill: #C8C8C8; -fx-font-size: 20px;"
            isWrapText = true
            maxWidth = Double.MAX_VALUE
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        val dismiss = Label("✕").apply {
            style = "-fx-text-fill: $MUTED_COLOUR; -fx-font-size: 18px; -fx-padding: 0 4 0 8;"
            cursor = Cursor.HAND
            setOnMouseClicked { event ->
                event.consume()
                row.dismissed = true
                redraw(index)
            }
        }
        val parts = ArrayList<Node>()
        parts += HBox(question, dismiss).apply {
            alignment = Pos.TOP_LEFT
            padding = Insets(6.0, 8.0, 6.0, 8.0)
            background = questionBackground
        }
        parts += richText(row.answer)
        row.expansions.forEach { expansion ->
            parts += Separator()
            if (expansion.text.isBlank() && !expansion.done) parts += muted("Writing a fuller answer…")
            if (expansion.text.isNotBlank()) parts += richText(expansion.text)
            expansion.failure?.let { parts += muted("Stopped: $it") }
        }
        if (row.open) parts += followUpList(index, row)
        return VBox(6.0).apply {
            children.addAll(parts)
            padding = Insets(10.0)
            background = entryBackground
            border = entryBorder
            alignment = Pos.TOP_LEFT
            cursor = Cursor.HAND
            isVisible = !row.dismissed
            isManaged = !row.dismissed
            setOnMouseClicked {
                row.open = !row.open
                if (row.open) askFollowUps(index)
                redraw(index)
            }
        }
    }

    /** The follow-up questions under an answer, one per line with a rule between them. */
    private fun followUpList(index: Int, row: Row): VBox {
        fun line(text: String, colour: String, onClick: (() -> Unit)?) = Label(text).apply {
            style = "-fx-text-fill: $colour; -fx-font-size: 18px; -fx-padding: 8 10 8 10;"
            isWrapText = true
            maxWidth = Double.MAX_VALUE
            if (onClick != null) {
                cursor = Cursor.HAND
                setOnMouseClicked { event ->
                    event.consume()
                    onClick()
                }
            }
        }
        val items = ArrayList<Node>()
        when (val followUps = row.followUps) {
            FollowUps.NotAsked, FollowUps.Loading -> items += line("Finding follow-up questions…", MUTED_COLOUR, null)
            is FollowUps.Failed -> items += line("No follow-up questions: ${followUps.reason}. Click to try again.", MUTED_COLOUR) {
                askFollowUps(index)
            }
            is FollowUps.Ready -> followUps.questions.forEachIndexed { i, question ->
                if (i > 0) items += Separator()
                items += line(question, LINK_COLOUR) { expand(index, question) }
            }
        }
        return VBox().apply {
            children.addAll(items)
            background = Background(BackgroundFill(Color.web("#282828"), CornerRadii(8.0), Insets.EMPTY))
        }
    }

    /** A model's text: prose with `inline code` in monospace, fenced code in a box of its own that does not wrap. */
    private fun richText(text: String): VBox {
        val box = VBox(6.0)
        splitCodeBlocks(text).forEach { part ->
            if (part.code) {
                val code = TextFlow().apply {
                    padding = Insets(10.0)
                    CodeHighlighter.highlight(part.text, part.language).forEach { token ->
                        children += Text(token.text).apply {
                            font = Font.font(CODE_FONT, CODE_FONT_SIZE)
                            fill = Color.web(CODE_COLOURS.getValue(token.kind))
                        }
                    }
                }
                box.children += ScrollPane(code).apply {
                    isFitToHeight = true
                    vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                    hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
                    style = "-fx-background: $CODE_BACKGROUND; -fx-background-color: $CODE_BACKGROUND; -fx-background-radius: 8;"
                    // The app's stylesheet paints the viewport over that colour: the code paints its own, across the width.
                    code.background = Background(BackgroundFill(Color.web(CODE_BACKGROUND), CornerRadii(8.0), Insets.EMPTY))
                    code.minWidthProperty().bind(viewportBoundsProperty().map { it.width })
                    // A click copies the code instead of opening the follow-ups.
                    Tooltip.install(this, Tooltip("Click to copy"))
                    setOnMouseClicked { event ->
                        event.consume()
                        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(part.text) })
                        code.opacity = 0.5
                        PauseTransition(Duration.millis(200.0)).apply { setOnFinished { code.opacity = 1.0 } }.play()
                    }
                }
            } else {
                box.children += prose(part.text.replace(Regex("""\n\s*\n"""), "\n"))
            }
        }
        return box
    }

    private fun prose(text: String): TextFlow {
        val flow = TextFlow()
        text.split('`').forEachIndexed { i, piece ->
            val term = i % 2 == 1
            flow.children += Text(piece).apply {
                fill = Color.web(if (term) TERM_COLOUR else TEXT_COLOUR)
                font = if (term) Font.font(CODE_FONT, FontWeight.BOLD, ANSWER_FONT_SIZE) else Font.font(ANSWER_FONT_SIZE)
            }
        }
        return flow
    }

    private fun muted(text: String) = Label(text).apply { style = "-fx-text-fill: $MUTED_COLOUR; -fx-font-size: 16px;" }

    // With no scrollbar yet the view counts as at the bottom, so the first rows scroll into view.
    private fun isScrolledToBottom(): Boolean {
        val viewport = scrollPane.viewportBounds.height
        val content = scrollPane.content?.layoutBounds?.height ?: 0.0
        return content <= viewport || scrollPane.vvalue >= scrollPane.vmax - 0.01
    }

    private fun scrollToBottom() {
        scrollPane.applyCss()
        scrollPane.layout()
        scrollPane.vvalue = scrollPane.vmax
    }
}
