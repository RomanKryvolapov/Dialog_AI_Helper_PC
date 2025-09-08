package ui

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import PROMPT_FULL_SIZE
import SOURCE_TEXT_CLEAR
import app.DialogApplication.Companion.ownerStage
import backgroundThreadScope
import dev.langchain4j.model.input.PromptTemplate
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
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.VoskVoiceRecognizer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class MessagesTab(
    private val voskVoiceRecognizer: VoskVoiceRecognizer,
    private val cloudRepository: CloudRepository,
    private val localNetworkRepository: LocalNetworkRepository,
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {


    private val log = LoggerFactory.getLogger("MessagesTab")

    companion object {
        const val FIELD_BACKGROUND_COLOUR = "#323232"
        const val BUTTONS_BACKGROUND_COLOUR = "#323232"
        private const val TRANSLATE_CHECK_DELAY = 1000L
        private const val NEW_MESSAGE_DELTA = 16
    }

    val content: Pane
    val scrollPane: ScrollPane

    @Volatile
    private var originalMessage = ""

    @Volatile
    private var lastMessageToAI = "..."

    @Volatile
    private var lastMessageFromAI = "..."

    private val lastTranslatedLength = AtomicInteger(0)
    private val lastTranslatedTime = AtomicLong(System.currentTimeMillis())

    private var translateMessageJob: Job? = null
    private var translateTimerJob: Job? = null

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
                        originalMessage = "..."
                        lastMessageFromAI = "..."
                        lastMessageToAI = ""
                        lastTranslatedLength.set(0)
                        lastTranslatedTime.set(System.currentTimeMillis())
                        translateMessageJob?.cancel()
                        translateMessageJob = null
                        translateTimerJob?.cancel()
                        translateTimerJob = null
                    }
                },
                Button("Restart engine").apply {
                    style += "-fx-background-color: $COLOUR_RED;"
                    setOnAction {
                        val device = voskVoiceRecognizer.getAvailableInputDevices().firstOrNull {
                            getAppInfo().lastSelectedDevice == it.name
                        }
                        if (device == null) {
                            ownerStage?.showAlert(
                                alertTitle = "No Device Selected",
                                alertContent = "Please select an audio device in the Settings tab before starting recognition."
                            )
                            return@setOnAction
                        }
                        voskVoiceRecognizer.runRecognition(device)
                    }
                },
                Button("Split sentence").apply {
                    style += "-fx-background-color: $COLOUR_BLUE;"
                    setOnAction {
                        voskVoiceRecognizer.splitUtterance()
                    }
                },
                Button("Send now").apply {
                    style += "-fx-background-color: $COLOUR_GREEN;"
                    setOnAction {
                        translateMessage(
                            index = messageBox.children.size - 1,
                            message = originalMessage,
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
        val device = voskVoiceRecognizer.getAvailableInputDevices().firstOrNull {
            getAppInfo().lastSelectedDevice == it.name
        }
        if (device == null) {
            ownerStage?.showAlert(
                alertTitle = "No Device Selected",
                alertContent = "Please select an audio device in the Settings tab before starting recognition."
            )
            return
        }
        when {
            voskVoiceRecognizer.inInit() -> {
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = "Please wait for the initialization to complete."
                )
            }

            !voskVoiceRecognizer.inRunning() -> {
                voskVoiceRecognizer.runRecognition(device)
                translateTimerJob?.cancel()
                translateTimerJob = backgroundThreadScope.launch {
                    val translateTextEveryMilliseconds = getAppInfo().sendTextEveryMilliseconds
                    if (translateTextEveryMilliseconds != 0L) {
                        while (voskVoiceRecognizer.inRunning()) {
                            delay(TRANSLATE_CHECK_DELAY)
                            val currentTime = System.currentTimeMillis()
                            if (lastTranslatedTime.get() + translateTextEveryMilliseconds < currentTime) {
                                log.debug("Translate text by timer")
                                translateMessage(
                                    index = messageBox.children.size - 1,
                                    message = originalMessage,
                                    writeResultToMessageBuffer = true,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                voskVoiceRecognizer.stopRecognition()
            }
        }
    }

    private fun setupListeners() {
        voskVoiceRecognizer.onInitReady = {
            mainThreadScope.launch {
                listeningButton.text = "Start dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_GREEN;"
            }
        }
        voskVoiceRecognizer.onStartListener = {
            mainThreadScope.launch {
                listeningButton.text = "Stop dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
            }
        }
        voskVoiceRecognizer.onStopListener = {
            mainThreadScope.launch {
                listeningButton.text = "Start dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_GREEN;"
            }
        }
        voskVoiceRecognizer.onErrorListener = { message ->
            mainThreadScope.launch {
                listeningButton.text = "Error init"
                listeningButton.style += "-fx-background-color: $COLOUR_RED;"
            }
            ownerStage?.showAlert(
                alertTitle = "Recognizer error",
                alertContent = message
            )
        }
        voskVoiceRecognizer.onResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty()) {
                mainThreadScope.launch {
                    listeningButton.text = "Stop dialog"
                    listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
                }
            }
        }
        voskVoiceRecognizer.onPartialResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty() && resultString != "the" && originalMessage != resultString) {
                mainThreadScope.launch {
                    listeningButton.text = "Stop dialog"
                    listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
                }
                val translateTextEverySymbols = getAppInfo().sendTextEverySymbols
                val currentMessageIndex = messageBox.children.size - 1
                when {
                    // New message
                    originalMessage.length > resultString.length + NEW_MESSAGE_DELTA -> {
                        updateMessageAt(
                            index = currentMessageIndex + 1,
                            newItem = DialogItem(
                                originalMessage = resultString,
                                messageFromAi = "...",
                            )
                        )
                        translateMessage(
                            index = currentMessageIndex,
                            message = originalMessage,
                            writeResultToMessageBuffer = false,
                        )
                        originalMessage = resultString
                        lastMessageFromAI = "..."
                        lastMessageToAI = ""
                        lastTranslatedLength.set(0)
                    }

                    translateTextEverySymbols != 0 &&
                            (originalMessage.length / translateTextEverySymbols) > (lastTranslatedLength.get() / translateTextEverySymbols) -> {
                        log.debug("Translate text by text size")
                        lastTranslatedLength.set(originalMessage.length)
                        originalMessage = resultString
                        updateMessageAt(
                            index = currentMessageIndex,
                            newItem = DialogItem(
                                originalMessage = resultString,
                                messageFromAi = lastMessageFromAI,
                            )
                        )
                        translateMessage(
                            index = currentMessageIndex,
                            message = resultString,
                            writeResultToMessageBuffer = true,
                        )
                    }

                    else -> {
                        originalMessage = resultString
                        updateMessageAt(
                            index = currentMessageIndex,
                            newItem = DialogItem(
                                originalMessage = resultString,
                                messageFromAi = lastMessageFromAI,
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
        if (message.isBlank() || message == "the") {
            lastTranslatedTime.set(System.currentTimeMillis())
            log.error("Translate message is empty")
            return
        }
        if (lastMessageToAI == message) {
            lastTranslatedTime.set(System.currentTimeMillis())
            log.error("Translate message is already translated")
            return
        }
        translateMessageJob?.cancel()
        translateMessageJob = backgroundThreadScope.launch {
            lastTranslatedTime.set(System.currentTimeMillis())
            val appInfo = getAppInfo()
            log.debug("Translate text: $message")
            val template = PromptTemplate.from(PROMPT_FULL_SIZE)
            val prompt = template.apply(mapOf(SOURCE_TEXT_CLEAR to message))
            val result = when (appInfo.selectedModel.engine) {
                LlmModelEngine.GOOGLE -> {
                    cloudRepository.generateAnswerByLangChainByGoogleAI(
                        prompt = prompt,
                        apiKey = appInfo.googleCloudToken,
                        model = appInfo.selectedModel.id,
                    )
                }

                LlmModelEngine.LM_STUDIO -> {
                    localNetworkRepository.generateAnswerByLangChainLmStudio(
                        prompt = prompt,
                        baseUrl = appInfo.lmStudioConfig.baseUrl,
                        model = appInfo.selectedModel.id,
                    )
                }

                LlmModelEngine.OLLAMA -> {
                    localNetworkRepository.generateAnswerByLangChainOllama(
                        prompt = prompt,
                        baseUrl = appInfo.ollamaConfig.baseUrl,
                        model = appInfo.selectedModel.id,
                    )
                }
            }
            if (!result.isSuccess) {
                log.error("Translate message error: ${result.exceptionOrNull()}")
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = result.exceptionOrNull()?.message ?: ""
                )
                updateMessageAt(
                    index = index,
                    newItem = DialogItem(
                        originalMessage = message,
                        messageFromAi = "...",
                    )
                )
                return@launch
            }
            val resultMessage = result.getOrNull()
            if (resultMessage.isNullOrBlank()) {
                log.error("Translate message result is null")
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = "Answer message is null or blank"
                )
                updateMessageAt(
                    index = index,
                    newItem = DialogItem(
                        originalMessage = message,
                        messageFromAi = "...",
                    )
                )
                return@launch
            }
            log.debug("Translate message result:\n$resultMessage")
            lastMessageToAI = message
            lastMessageFromAI = if (writeResultToMessageBuffer) {
                resultMessage
            } else {
                "..."
            }
            updateMessageAt(
                index = index,
                newItem = DialogItem(
                    originalMessage = message,
                    messageFromAi = resultMessage,
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
        val descLabel = Label(item.messageFromAi).apply {
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
