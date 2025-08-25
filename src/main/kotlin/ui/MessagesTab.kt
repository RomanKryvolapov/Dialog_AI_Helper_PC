package ui

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import PROMPT_FULL_SIZE
import TRANSLATE_FROM_LANGUAGE_CLEAR
import TRANSLATE_TEXT_CLEAR
import TRANSLATE_TO_LANGUAGE_CLEAR
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
import utils.VoskRecognizer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class MessagesTab(
    private val voskRecognizer: VoskRecognizer,
    private val cloudRepository: CloudRepository,
    private val localNetworkRepository: LocalNetworkRepository,
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {


    private val log = LoggerFactory.getLogger("MessagesTabTag")

    companion object {
        const val FIELD_BACKGROUND_COLOUR = "#323232"
        const val BUTTONS_BACKGROUND_COLOUR = "#323232"
        private const val TRANSLATE_CHECK_DELAY = 1000L
    }

    val content: Pane
    val scrollPane: ScrollPane

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
                        val device = voskRecognizer.getAvailableInputDevices().firstOrNull {
                            getAppInfo().lastSelectedDevice == it.name
                        }
                        if (device == null) {
                            showAlert(
                                alertTitle = "No Device Selected",
                                alertContent = "Please select an audio device in the Settings tab before starting recognition."
                            )
                            return@setOnAction
                        }
                        voskRecognizer.runRecognition(device)
                    }
                },
                Button("Restart recognition").apply {
                    style += "-fx-background-color: $COLOUR_BLUE;"
                    setOnAction {
                        voskRecognizer.splitUtterance()
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
        val device = voskRecognizer.getAvailableInputDevices().firstOrNull {
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
            voskRecognizer.inInit() -> {
                showAlert(
                    alertTitle = "Error",
                    alertContent = "Please wait for the initialization to complete."
                )
            }

            !voskRecognizer.inRunning() -> {
                voskRecognizer.runRecognition(device)
                backgroundThreadScope.launch {
                    val translateTextEveryMilliseconds = getAppInfo().translateTextEveryMilliseconds
                    if (translateTextEveryMilliseconds != 0L) {
                        delay(3000L)
                        while (voskRecognizer.inRunning()) {
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
                voskRecognizer.stopRecognition()
            }
        }
    }

    private fun setupListeners() {
        voskRecognizer.onInitReady = {
            log.debug("onInitReady")
            mainThreadScope.launch {
                listeningButton.text = "Start dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_GREEN;"
            }
        }
        voskRecognizer.onStartListener = {
            log.debug("onStartListener")
            mainThreadScope.launch {
                listeningButton.text = "Stop dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
            }
        }
        voskRecognizer.onStopListener = {
            log.debug("onStopListener")
            mainThreadScope.launch {
                listeningButton.text = "Start dialog"
                listeningButton.style += "-fx-background-color: $COLOUR_GREEN;"
            }
        }
        voskRecognizer.onErrorListener = { message ->
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
        voskRecognizer.onResultListener = { result ->
            val resultString = JSONObject(result).optString("partial").normalizeAndRemoveEmptyLines()
            if (resultString.isNotEmpty()) {
                log.debug("onResultListener: $resultString")
                mainThreadScope.launch {
                    listeningButton.text = "Stop dialog"
                    listeningButton.style += "-fx-background-color: $COLOUR_BLUE;"
                }
            }
        }
        voskRecognizer.onPartialResultListener = { result ->
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
                            index = currentMessageIndex,
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
//            val text = appInfo.prompt
//                .replace(TRANSLATE_FROM_LANGUAGE, appInfo.selectedFromLanguage.englishNameString)
//                .replace(TRANSLATE_TO_LANGUAGE, appInfo.selectedToLanguage.englishNameString)
//                .replace(TRANSLATE_TEXT, message)
            log.debug("translateMessage message: $message from: ${appInfo.selectedFromLanguage.englishNameString} to: ${appInfo.selectedToLanguage.englishNameString}")
            val template = PromptTemplate.from(PROMPT_FULL_SIZE)
            val prompt = template.apply(
                mapOf(
                    TRANSLATE_FROM_LANGUAGE_CLEAR to appInfo.selectedFromLanguage.englishNameString,
                    TRANSLATE_TO_LANGUAGE_CLEAR to appInfo.selectedToLanguage.englishNameString,
                    TRANSLATE_TEXT_CLEAR to message
                )
            )
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
                        port = appInfo.lmStudioConfig.port,
                        model = appInfo.selectedModel.id,
                    )
                }

                LlmModelEngine.OLLAMA -> {
                    localNetworkRepository.generateAnswerByLangChainOllama(
                        prompt = prompt,
                        port = appInfo.ollamaConfig.port,
                        model = appInfo.selectedModel.id,
                    )
                }
            }
            if (!result.isSuccess) {
                log.error("translateMessage result error: ${result.exceptionOrNull()}")
                showAlert(
                    alertTitle = "Error",
                    alertContent = result.exceptionOrNull()?.message ?: ""
                )
                updateMessageAt(
                    index = index,
                    newItem = DialogItem(
                        originalMessage = message,
                        answerMessage = "...",
                    )
                )
                return@launch
            }
            val resultMessage = result.getOrNull()
            if (resultMessage.isNullOrBlank()) {
                log.error("translateMessage resultMessage is null")
                showAlert(
                    alertTitle = "Error",
                    alertContent = "Answer message is null or blank"
                )
                updateMessageAt(
                    index = index,
                    newItem = DialogItem(
                        originalMessage = message,
                        answerMessage = "...",
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
