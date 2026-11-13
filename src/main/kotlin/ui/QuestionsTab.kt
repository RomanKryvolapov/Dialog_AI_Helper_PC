package ui

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.Border
import javafx.scene.layout.BorderStroke
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.layout.BorderWidths
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import utils.QuestionsDisplay

/**
 * The Questions screen: every question the assistant found in the conversation with its answer, newest at the
 * bottom, built like the Messages screen. A phrase without a question adds nothing here.
 */
class QuestionsTab : QuestionsDisplay {

    private companion object {
        const val BACKGROUND_COLOUR = "#323232"
    }

    private val rows = VBox(4.0).apply {
        padding = Insets(4.0)
        background = Background(BackgroundFill(Color.web(BACKGROUND_COLOUR), null, null))
    }

    private val scrollPane = ScrollPane(rows).apply {
        isFitToWidth = true
        style = "-fx-background: $BACKGROUND_COLOUR; -fx-background-color: $BACKGROUND_COLOUR;"
    }

    val content: ScrollPane get() = scrollPane

    private val entryBackground = Background(BackgroundFill(Color.web("#2f2f2f"), CornerRadii(8.0), Insets.EMPTY))
    private val entryBorder = Border(BorderStroke(Color.web("#444444"), BorderStrokeStyle.SOLID, CornerRadii(8.0), BorderWidths(1.0)))

    // Rows handed out; counted here, not read from the list, which the FX thread fills later in the same order.
    private var count = 0

    /** Adds a found question with its answer so far; the returned row is passed back to [update] as the answer grows. */
    override fun add(question: String, answer: String): Int {
        val index = synchronized(this) { count++ }
        Platform.runLater {
            val atBottom = isScrolledToBottom()
            rows.children.add(entry(question, answer))
            if (atBottom) scrollToBottom()
        }
        return index
    }

    override fun update(row: Int, question: String, answer: String) {
        Platform.runLater {
            if (row !in 0 until rows.children.size) return@runLater
            val atBottom = isScrolledToBottom()
            rows.children[row] = entry(question, answer)
            if (atBottom) scrollToBottom()
        }
    }

    override fun clear() {
        synchronized(this) { count = 0 }
        Platform.runLater { rows.children.clear() }
    }

    private fun entry(question: String, answer: String): VBox {
        val questionLabel = Label(question).apply {
            styleClass.add("original-message-label")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        val answerLabel = Label(answer).apply {
            styleClass.add("answer-message-label")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        return VBox(5.0, questionLabel, answerLabel).apply {
            padding = Insets(10.0)
            background = entryBackground
            border = entryBorder
            alignment = Pos.TOP_LEFT
            questionLabel.prefWidthProperty().bind(widthProperty())
            answerLabel.prefWidthProperty().bind(widthProperty())
        }
    }

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
