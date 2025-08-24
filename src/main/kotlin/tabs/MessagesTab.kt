package tabs

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import TRANSLATE_FROM_LANGUAGE
import TRANSLATE_TEXT
import TRANSLATE_TO_LANGUAGE
import backgroundThreadScope
import extensions.normalizeAndRemoveEmptyLines
import extensions.showAlert
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.Stage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.DialogItem
import models.domain.LlmModelEngine
import org.json.JSONObject
import org.slf4j.LoggerFactory
import repository.CloudRepository
import repository.LocalNetworkRepository
import utils.VoskRecognizer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object MessagesTab : BaseTab() {

    private val log = LoggerFactory.getLogger("MessagesTabTag")

    const val FIELD_BACKGROUND_COLOUR = "#323232"
    const val BUTTONS_BACKGROUND_COLOUR = "#323232"
    private const val TRANSLATE_CHECK_DELAY = 1000L

    val content: Pane
    val scrollPane: ScrollPane
    var ownerStage: Stage? = null

    private var originalMessageBuffer = ""
    private var translatedMessageBuffer = "..."

    private val lastTranslatedLength = AtomicInteger(0)
    private val lastTranslatedTime = AtomicLong(System.currentTimeMillis())

    private var translateMessageJob: Job? = null

    private var listeningButton = Button("Wait for init").apply {
        style += "-fx-background-color: $COLOUR_RED;"
        setOnAction {
            startListening()
        }
    }

    private val messageBox = VBox(4.0).apply {
        padding = Insets(4.0)
        background = Background(BackgroundFill(Color.web(FIELD_BACKGROUND_COLOUR), null, null))
    }

    init {
        setupListeners()

        scrollPane = ScrollPane(messageBox).apply {
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
                    setOnAction {
                        messageBox.children.clear()
                        originalMessageBuffer = "..."
                        translatedMessageBuffer = "..."
                        lastTranslatedLength.set(0)
                        lastTranslatedTime.set(System.currentTimeMillis())
                        translateMessageJob?.cancel()
                        translateMessageJob = null
                    }
                },
                Button("Restart engine").apply {
                    style += "-fx-background-color: $COLOUR_RED;"
                    setOnAction {
                        val device = VoskRecognizer.getAvailableInputDevices().firstOrNull {
                            getAppInfo().lastSelectedDevice == it.name
                        }
                        if (device == null) {
                            showAlert(
                                alertTitle = "No Device Selected",
                                alertContent = "Please select an audio device in the Settings tab before starting recognition."
                            )
                            return@setOnAction
                        }
                        VoskRecognizer.runRecognition(device)
                    }
                },
                Button("Restart recognition").apply {
                    style += "-fx-background-color: $COLOUR_BLUE;"
                    setOnAction {
                        VoskRecognizer.splitUtterance()
                    }
                },
                Button("Translate now").apply {
                    style += "-fx-background-color: $COLOUR_GREEN;"
                    setOnAction {
                        translateMessage(
                            index = messageBox.children.size - 1,
                            message = originalMessageBuffer,
                            writeResultToMessageBuffer = true,
                        )
                    }
                },
                listeningButton,
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

    private fun startListening() {
        val device = VoskRecognizer.getAvailableInputDevices().firstOrNull {
            getAppInfo().lastSelectedDevice == it.name
        }
        if (device == null) {
            showAlert(
                alertTitle = "No Device Selected",
                alertContent = "Please select an audio device in the Settings tab before starting recognition."
            )
            return
        }
        when {
            VoskRecognizer.inInit() -> {
                showAlert(
                    alertTitle = "Error",
                    alertContent = "Please wait for the initialization to complete."
                )
            }

            !VoskRecognizer.inRunning() -> {
                VoskRecognizer.runRecognition(device)
                backgroundThreadScope.launch {
                    val translateTextEveryMilliseconds = getAppInfo().translateTextEveryMilliseconds
                    if (translateTextEveryMilliseconds != 0L) {
                        delay(3000L)
                        while (VoskRecognizer.inRunning()) {
                            delay(TRANSLATE_CHECK_DELAY)
                            val currentTime = System.currentTimeMillis()
                            if (lastTranslatedTime.get() + translateTextEveryMilliseconds < currentTime) {
                                log.debug("translate by timer")
                                translateMessage(
                                    index = messageBox.children.size - 1,
                                    message = originalMessageBuffer,
                                    writeResultToMessageBuffer = true,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                VoskRecognizer.stopRecognition()
            }
        }
    }

    private fun setupListeners() {
        VoskRecognizer.onInitReady = {
            log.debug("onInitReady")
            mainThreadScope.launch {
                listeningButton.text = "Start dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_GREEN;"
            }
        }
        VoskRecognizer.onStartListener = {
            log.debug("onStartListener")
            mainThreadScope.launch {
                listeningButton.text = "Stop dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
            }
        }
        VoskRecognizer.onStopListener = {
            log.debug("onStopListener")
            mainThreadScope.launch {
                listeningButton.text = "Start dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_GREEN;"
            }
        }
        VoskRecognizer.onErrorListener = { message ->
            log.error("onErrorListener: $message")
            mainThreadScope.launch {
                listeningButton.text = "Error init"
                listeningButton.style += "-fx-background-color: $COLOUR_RED;"
            }
            showAlert(
                alertTitle = "Recognizer error",
                alertContent = message
            )
        }
        VoskRecognizer.onResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty()) {
                log.debug("onResultListener: $resultString")
                mainThreadScope.launch {
                    listeningButton.text = "Stop dialog"
                    listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
                }
            }
        }
        VoskRecognizer.onPartialResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty() && resultString != "the" && originalMessageBuffer != result) {
                mainThreadScope.launch {
                    listeningButton.text = "Stop dialog"
                    listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
                }
                val translateTextEverySymbols = getAppInfo().translateTextEverySymbols
                val currentMessageIndex = messageBox.children.size - 1
                when {
                    originalMessageBuffer.length > resultString.length + 16 -> {
                        log.debug("onPartialResultListener new message: $resultString")
                        updateMessageAt(
                            index = currentMessageIndex + 1,
                            newItem = DialogItem(
                                originalMessage = resultString,
                                answerMessage = "...",
                            )
                        )
                        translateMessage(
                            index = currentMessageIndex,
                            message = originalMessageBuffer,
                            writeResultToMessageBuffer = false,
                        )
                        originalMessageBuffer = resultString
                        translatedMessageBuffer = "..."
                        lastTranslatedLength.set(0)
                    }

                    translateTextEverySymbols != 0 &&
                            (originalMessageBuffer.length / translateTextEverySymbols) > (lastTranslatedLength.get() / translateTextEverySymbols) -> {
                        log.debug("translate by size")
                        lastTranslatedLength.set(originalMessageBuffer.length)
                        originalMessageBuffer = resultString
                        updateMessageAt(
                            index = currentMessageIndex,
                            newItem = DialogItem(
                                originalMessage = resultString,
                                answerMessage = translatedMessageBuffer,
                            )
                        )
                        translateMessage(
                            index = currentMessageIndex,
                            message = resultString,
                            writeResultToMessageBuffer = true,
                        )
                    }

                    else -> {
                        originalMessageBuffer = resultString
                        updateMessageAt(
                            index =currentMessageIndex,
                            newItem = DialogItem(
                                originalMessage = resultString,
                                answerMessage = translatedMessageBuffer,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun translateMessage(
        index: Int,
        message: String,
        writeResultToMessageBuffer: Boolean,
    ) {
        if (message.isEmpty()) {
            log.error("translateMessage message is empty")
            return
        }
        translateMessageJob?.cancel()
        translateMessageJob = backgroundThreadScope.launch {
            lastTranslatedTime.set(System.currentTimeMillis())
            val appInfo = getAppInfo()
            val text = appInfo.prompt
                .replace(TRANSLATE_FROM_LANGUAGE, appInfo.selectedFromLanguage.englishNameString)
                .replace(TRANSLATE_TO_LANGUAGE, appInfo.selectedToLanguage.englishNameString)
                .replace(TRANSLATE_TEXT, message)
            val result = when (appInfo.selectedModel.engine) {
                LlmModelEngine.GOOGLE -> {
                    CloudRepository.generateAnswerByGoogleAI(
                        text = text,
                        apiKey = appInfo.googleCloudToken,
                        model = appInfo.selectedModel.id,
                    )
                }
                LlmModelEngine.LOCALHOST -> {
                    LocalNetworkRepository.generateAnswerByLmStudio(
                        port = appInfo.lmStudioPort,
                        text = text,
                        model = appInfo.selectedModel.id,
                    )
                }
            }
            if (!result.isSuccess) {
                log.error("translateMessage result error: ${result.exceptionOrNull()}")
                updateMessageAt(
                    index = index,
                    newItem = DialogItem(
                        originalMessage = message,
                        answerMessage = result.exceptionOrNull()?.message ?: "",
                    )
                )
                return@launch
            }
            val resultMessage = result.getOrNull()
            if (resultMessage == null) {
                log.error("translateMessage resultMessage is null")
                updateMessageAt(
                    index = index,
                    newItem = DialogItem(
                        originalMessage = message,
                        answerMessage = "Error",
                    )
                )
                return@launch
            }
            log.debug("translateMessage result: $resultMessage")
            translatedMessageBuffer = if (writeResultToMessageBuffer) {
                resultMessage
            } else {
                "..."
            }
            updateMessageAt(
                index = index,
                newItem = DialogItem(
                    originalMessage = message,
                    answerMessage = resultMessage,
                )
            )
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
            scrollPane.vvalue = 1.0
        }
    }

    private fun createMessageEntry(item: DialogItem): VBox {
        val titleLabel = Label(item.originalMessage).apply {
            styleClass.add("original-message-label")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        val descLabel = Label(item.answerMessage).apply {
            styleClass.add("answer-message-label")
            isWrapText = true
            maxWidth = Double.MAX_VALUE
        }
        return VBox(5.0, titleLabel, descLabel).apply {
            padding = Insets(10.0)
            background = sharedEntryBackground
            border = sharedEntryBorder
            alignment = Pos.TOP_LEFT
            titleLabel.prefWidthProperty().bind(widthProperty())
            descLabel.prefWidthProperty().bind(widthProperty())
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
