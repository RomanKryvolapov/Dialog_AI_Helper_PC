package tabs

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import extensions.normalizeAndRemoveEmptyLines
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Stage
import models.domain.DialogItem
import org.json.JSONObject
import utils.VoskMicrophoneDemo

object MessagesTab: BaseTab() {

    const val FIELD_BACKGROUND_COLOUR = "#323232"

    const val BUTTONS_BACKGROUND_COLOUR = "#323232"

    const val LIST_ITEM_FIRST_TEXT_COLOUR = "#999999"
    const val LIST_ITEM_SECOND_TEXT_COLOUR = "#FFFFFF"

    val content: Pane

    var ownerStage: Stage? = null

    private var originalMessageBuffer = ""

    private val messageBox = VBox(4.0).apply {
        padding = Insets(4.0)
        background = Background(BackgroundFill(Color.web(FIELD_BACKGROUND_COLOUR), null, null))
    }

    init {
        setupListeners()

        val scrollPane = ScrollPane(messageBox).apply {
            isFitToWidth = true
            vbarPolicy = ScrollPane.ScrollBarPolicy.ALWAYS
        }

        val buttonBar = HBox(12.0).apply {
            alignment = Pos.CENTER
            padding = Insets(12.0)
            background = Background(BackgroundFill(Color.web(BUTTONS_BACKGROUND_COLOUR), null, null))

            val buttons = listOf(
                Button("Clear dialog").apply {
                    style += "-fx-background-color: $COLOUR_RED;"
                    setOnAction { messageBox.children.clear() }
                },
                Button("Restart engine").apply {
                    style += "-fx-background-color: $COLOUR_RED;"
                },
                Button("Restart recognition").apply {
                    style += "-fx-background-color: $COLOUR_BLUE;"
                },
                Button("Translate now").apply {
                    style += "-fx-background-color: $COLOUR_GREEN;"
                },
                Button("Start translate").apply {
                    style += "-fx-background-color: $COLOUR_BLUE;"
                    var isRunning = false
                    setOnAction {
                        val device = SettingsTab.getSelectedDevice()
                        if (device == null) {
                            Alert(Alert.AlertType.WARNING).apply {
                                ownerStage?.let { initOwner(it) }
                                title = "No Device Selected"
                                headerText = null
                                contentText =
                                    "Please select an audio device in the Settings tab before starting recognition."
                                showAndWait()
                            }
                            return@setOnAction
                        }

                        if (!isRunning) {
                            VoskMicrophoneDemo.runRecognition(device)
                            text = "Stop translate"
                            isRunning = true
                        } else {
                            VoskMicrophoneDemo.stopRecognition()
                            text = "Start translate"
                            isRunning = false
                        }
                    }
                }
            )
            buttons.forEach {
                HBox.setHgrow(it, Priority.ALWAYS)
                it.maxWidth = Double.MAX_VALUE
            }
            children.addAll(buttons)
        }

        content = BorderPane().apply {
            center = scrollPane
            bottom = buttonBar
            background = Background(BackgroundFill(Color.web("#2b2b2b"), null, null))
        }
    }

    private fun setupListeners() {
        VoskMicrophoneDemo.onStartListener = { println("onStartListener") }
        VoskMicrophoneDemo.onStopListener = { println("onStopListener") }
        VoskMicrophoneDemo.onErrorListener = { error -> println("onErrorListener: $error") }
        VoskMicrophoneDemo.onResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty()) {
                println("onResultListener: $resultString")
            }
        }
        VoskMicrophoneDemo.onPartialResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty() && originalMessageBuffer != result) {
                when {
                    originalMessageBuffer.length > resultString.length + 16 -> {
                        println("onPartialResultListener new message: $resultString")
                        updateMessageAt(
                            index = messageBox.children.size,
                            newItem = DialogItem(
                                title = originalMessageBuffer,
                                description = "",
                            )
                        )
                        originalMessageBuffer = resultString
                    }

                    else -> {
                        println("onPartialResultListener: $resultString")
                        originalMessageBuffer = resultString
                        updateMessageAt(
                            index = messageBox.children.size - 1,
                            newItem = DialogItem(
                                title = originalMessageBuffer,
                                description = "",
                            )
                        )
                    }
                }
            }
        }
    }

    private fun updateMessageAt(index: Int, newItem: DialogItem) {
        Platform.runLater {
            val newEntry = createMessageEntry(newItem)
            if (index in 0 until messageBox.children.size) {
                messageBox.children[index] = newEntry
            } else {
                messageBox.children.add(newEntry)
            }
        }
    }

    private fun createMessageEntry(item: DialogItem): VBox {
        val titleLabel = Label(item.title).apply {
            style = """
                -fx-text-fill: $LIST_ITEM_FIRST_TEXT_COLOUR; 
                -fx-font-size: 16px;
            """.trimIndent()
            isWrapText = true
        }
        val descLabel = Label(item.description).apply {
            style = """
                -fx-text-fill: $LIST_ITEM_SECOND_TEXT_COLOUR; 
                -fx-font-size: 18px;
            """.trimIndent()
            isWrapText = true
        }
        return VBox(5.0, titleLabel, descLabel).apply {
            padding = Insets(10.0)
            background = sharedEntryBackground
            border = sharedEntryBorder
            alignment = Pos.TOP_LEFT
        }
    }

    private val sharedEntryBackground = Background(
        BackgroundFill(Color.web("#2f2f2f"), CornerRadii(8.0), Insets.EMPTY)
    )

    private val sharedEntryBorder = Border(
        BorderStroke(
            Color.web("#444444"),
            BorderStrokeStyle.SOLID,
            CornerRadii(8.0),
            BorderWidths(1.0)
        )
    )
}
