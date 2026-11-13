package ui

import COLOUR_RED
import extensions.addButtons
import extensions.addSection
import extensions.copyButton
import extensions.settingsButton
import extensions.settingsPage
import javafx.application.Platform
import javafx.scene.control.TextArea
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
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

    private val log = LoggerFactory.getLogger("ConsoleTab")

    private val logArea = TextArea().apply {
        isEditable = false
        isWrapText = true
        prefRowCount = 16
        VBox.setVgrow(this, Priority.ALWAYS)
    }

    // The log takes the height that is left, so it is as tall as the window allows.
    val content: VBox = settingsPage {
        val section = addSection("Application messages") {
            children.add(logArea)
            addButtons(
                copyButton(logArea),
                settingsButton("Clear", COLOUR_RED) { logArea.text = "" },
            )
        }
        VBox.setVgrow(section, Priority.ALWAYS)
    }

    init {
        redirectSystemStreams { text ->
            Platform.runLater {
                logArea.appendText(text)
            }
        }
    }

    private fun redirectSystemStreams(logConsumer: (String) -> Unit) {
        val originalOut = System.out
        val originalErr = System.err

        val teeOut = TeeOutputStream(originalOut) { logConsumer(it) }
        val teeErr = TeeOutputStream(originalErr) { logConsumer(it) }

        System.setOut(PrintStream(teeOut, true))
        System.setErr(PrintStream(teeErr, true))
    }

    private class TeeOutputStream(
        private val original: OutputStream,
        private val consumer: (String) -> Unit
    ) : OutputStream() {

        override fun write(b: Int) {
            original.write(b)
            consumer(b.toChar().toString())
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            original.write(b, off, len)
            consumer(String(b, off, len))
        }

        override fun flush() {
            original.flush()
        }

        override fun close() {
            original.close()
        }
    }
}
