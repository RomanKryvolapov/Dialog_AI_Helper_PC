package ui

import COLOUR_GREEN
import COLOUR_RED
import extensions.addButton
import extensions.addTitleLabel
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.TextArea
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import ui.base.BaseTab
import java.io.OutputStream
import java.io.PrintStream

class ConsoleTab(
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
    }

    private val log = LoggerFactory.getLogger("ConsoleTab")

    private val logArea = TextArea().apply {
        isEditable = false
        isWrapText = true
        prefRowCount = 16
    }

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))
        addTitleLabel("Application messages:")
        children.add(logArea)

        addButton(
            title = "Clear",
            bgColor = COLOUR_RED,
            onClicked = {
                logArea.text = ""
            }
        )
    }

    init {
        redirectSystemStreams { text ->
            Platform.runLater {
                logArea.appendText(text)
            }
        }
    }

    private fun redirectSystemStreams(logConsumer: (String) -> Unit) {
        val out = object : OutputStream() {
            override fun write(b: Int) {
                logConsumer(b.toChar().toString())
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                logConsumer(String(b, off, len))
            }
        }
        System.setOut(PrintStream(out, true))
        System.setErr(PrintStream(out, true))
    }
}
