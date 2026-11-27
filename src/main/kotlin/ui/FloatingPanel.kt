package ui

import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.Border
import javafx.scene.layout.BorderStroke
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.layout.BorderWidths
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.StageStyle
import utils.WindowsDesktop
import utils.splitCodeBlocks

/**
 * A small window above all others with the newest question and its answer, for when the main window is out of the
 * way. It can be dragged anywhere; a click without a drag opens the main window. It keeps its bottom edge where it
 * was put, growing upwards as an answer gets longer. Touched on the FX thread only.
 */
class FloatingPanel(private val onClicked: () -> Unit) {

    private companion object {
        const val TITLE = "Dialog Application panel"
        const val WIDTH = 480.0
        const val MAX_TEXT_HEIGHT = 360.0
        const val SCREEN_MARGIN = 24.0
        const val FONT_SIZE = 15.0
        const val CODE_FONT = "Consolas"
        /** A press that moved less than this is a click, not a drag. */
        const val CLICK_SLOP = 4.0
    }

    private val question = Label().apply {
        isWrapText = true
        font = Font.font(null, FontWeight.BOLD, FONT_SIZE)
        textFill = Color.web("#C8C8C8")
        maxWidth = WIDTH - 32
    }

    private val answer = TextFlow().apply {
        maxWidth = WIDTH - 48
        lineSpacing = 2.0
    }

    private val body = VBox(8.0, question, answer).apply { padding = Insets(0.0, 4.0, 0.0, 0.0) }

    private val scroll = ScrollPane(body).apply {
        isFitToWidth = true
        maxHeight = MAX_TEXT_HEIGHT
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        style = "-fx-background: transparent; -fx-background-color: transparent;"
    }

    private val root = VBox(scroll).apply {
        padding = Insets(12.0, 14.0, 12.0, 14.0)
        prefWidth = WIDTH
        maxWidth = WIDTH
        background = Background(BackgroundFill(Color.web("#1E1F22"), CornerRadii(10.0), Insets.EMPTY))
        border = Border(BorderStroke(Color.web("#4A4D55"), BorderStrokeStyle.SOLID, CornerRadii(10.0), BorderWidths(1.0)))
    }

    private val stage = Stage(StageStyle.TRANSPARENT).apply {
        title = TITLE
        isAlwaysOnTop = true
        scene = Scene(root).apply { fill = Color.TRANSPARENT }
    }

    /** Where the panel's bottom edge sits; null until it is first shown. */
    private var bottom: Double? = null
    private var placedOffTaskbar = false

    init {
        var pressX = 0.0
        var pressY = 0.0
        var dragged = false
        root.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            pressX = e.screenX - stage.x
            pressY = e.screenY - stage.y
            dragged = false
        }
        root.addEventHandler(MouseEvent.MOUSE_DRAGGED) { e ->
            val x = e.screenX - pressX
            val y = e.screenY - pressY
            if (!dragged && Math.hypot(x - stage.x, y - stage.y) < CLICK_SLOP) return@addEventHandler
            dragged = true
            stage.x = x
            stage.y = y
            bottom = y + stage.height
        }
        root.addEventHandler(MouseEvent.MOUSE_RELEASED) { if (!dragged) onClicked() }
        stage.heightProperty().addListener { _, _, height -> bottom?.let { stage.y = it - height.toDouble() } }
        show(null)
    }

    /** The newest question and answer; null: none yet. */
    fun show(latest: QuestionsTab.Latest?) {
        if (latest == null) {
            question.text = "No answers yet"
            answer.children.setAll()
        } else {
            question.text = latest.question
            answer.children.setAll(
                splitCodeBlocks(latest.answer).flatMap { part ->
                    val text = Text(if (part.code) part.text else part.text.replace("`", "")).apply {
                        fill = Color.web(if (part.code) "#D4D4D4" else "#F0F0F0")
                        font = if (part.code) Font.font(CODE_FONT, FONT_SIZE - 1) else Font.font(FONT_SIZE)
                    }
                    listOf(text, Text("\n"))
                }.dropLast(1),
            )
        }
        if (stage.isShowing) stage.sizeToScene()
    }

    fun setOpacity(percent: Int) {
        stage.opacity = percent / 100.0
    }

    fun setShown(shown: Boolean) {
        if (shown == stage.isShowing) return
        if (!shown) {
            stage.hide()
            return
        }
        stage.show()
        stage.sizeToScene()
        if (bottom == null) {
            val screen = Screen.getPrimary().visualBounds
            stage.x = screen.maxX - stage.width - SCREEN_MARGIN
            bottom = screen.maxY - SCREEN_MARGIN
            stage.y = bottom!! - stage.height
        }
        // A window without a frame still gets a taskbar button of its own: the panel never needs one.
        if (!placedOffTaskbar) {
            WindowsDesktop.findWindow(TITLE)?.let { WindowsDesktop.setOffTaskbar(it, true) }
            placedOffTaskbar = true
        }
    }
}
