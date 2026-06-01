package ui

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
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
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.Border
import javafx.scene.layout.BorderPane
import javafx.scene.layout.BorderStroke
import javafx.scene.layout.BorderStrokeStyle
import javafx.scene.layout.BorderWidths
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.DialogItem
import models.domain.LlmModelEngine
import org.slf4j.LoggerFactory
import repository.CloudRepository
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.AppStatus
import utils.AudioChunker
import utils.DialogLogger
import utils.WhisperUtil

import dev.langchain4j.model.input.Prompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import models.domain.ApplicationInfo
import models.domain.LlmModel
import utils.TranslationRateLimiter

import models.domain.VoiceRecognitionEngine
import utils.WhisperStreamingUtil

import utils.SherpaOnnxRecognizer

import models.domain.SherpaModelConfig


class MessagesTab(
    private val whisperUtil: WhisperUtil,
    private val whisperStreamingUtil: WhisperStreamingUtil,
    private val sherpaRecognizer: SherpaOnnxRecognizer,
    private val audioChunker: AudioChunker,
    private val cloudRepository: CloudRepository,
    private val dialogLogger: DialogLogger,
    private val preferencesRepository: PreferencesRepository,
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    private val log = LoggerFactory.getLogger("MessagesTab")

    companion object {
        const val FIELD_BACKGROUND_COLOUR = "#323232"
        const val BUTTONS_BACKGROUND_COLOUR = "#323232"

        private const val MAX_TRANSLATION_QUEUE = 8
        private const val MAX_TRANSLATION_ATTEMPTS = 3
        private const val TRANSLATION_TTL_MILLIS = 40_000L
        private const val SCHEDULER_WAIT_SLICE_MILLIS = 1_000L
    }

    val content: Pane
    val scrollPane: ScrollPane

    @Volatile
    private var lastMessageFromAI = "..."

    private val rateLimiter = TranslationRateLimiter()
    private val translationQueueMutex = Mutex()
    private val translationQueue = ArrayList<TranslationTask>()
    private val schedulerWakeups = Channel<Unit>(Channel.CONFLATED)
    private var schedulerJob: Job? = null
    private var translationSeq = 0L

    /** FX-thread: recent recognized source texts, to feed previous-sentence context to the prompt. */
    private val recentSources = ArrayDeque<String>()

    /** Bumped on "Clear dialog" so in-flight results from a previous screen are ignored. */
    @Volatile
    private var translationEpoch = 0L

    /** UI-thread only: indexes of rows with placeholder text waiting for Whisper results. */
    private val pendingRowIndexes = ArrayDeque<Int>()

    private var labelMessagePartVoiceRecognition = ""
    private var labelMessagePartProcessMessage = ""
    private var labelMessagePartRecord = ""

    private val listeningButton = Button("Start dialog").apply {
        style = "-fx-background-color: $COLOUR_GREEN;"
        setOnAction { toggleDialog() }
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
                    style = "-fx-background-color: $COLOUR_RED;"
                    setOnAction {
                        messageBox.children.clear()
                        lastMessageFromAI = "..."
                        clearTranslations()
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

        // Auto-start microphone monitoring on selected device;
        // restart when device selection changes.
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow
                .map { it.lastSelectedDevice }
                .distinctUntilChanged()
                .collect { deviceName ->
                    restartMicrophone(deviceName)
                }
        }
    }

    private fun restartMicrophone(deviceName: String) {
        val device = audioChunker.getAvailableInputDevices().firstOrNull { it.name == deviceName }
        if (device == null) {
            log.warn("Selected device not found: $deviceName")
            if (audioChunker.inRunning()) audioChunker.stopListening()
            return
        }
        if (audioChunker.inRunning()) {
            audioChunker.stopListening()
        }
        audioChunker.startListening(device)
    }

    private fun toggleDialog() {
        if (audioChunker.chunkProcessingEnabled) {
            audioChunker.chunkProcessingEnabled = false
            Platform.runLater {
                listeningButton.text = "Start dialog"
                listeningButton.style = "-fx-background-color: $COLOUR_GREEN;"
            }
            labelMessagePartVoiceRecognition = ""
            updateLabelMessage()
            return
        }
        val appInfo = getAppInfo()
        if (appInfo.whisperModelPath.isBlank()) {
            ownerStage?.showAlert(
                alertTitle = "No Whisper model",
                alertContent = "Please select the folder with the Whisper model in the Settings tab."
            )
            return
        }
        if (!audioChunker.inRunning()) {
            val device = audioChunker.getAvailableInputDevices().firstOrNull {
                it.name == appInfo.lastSelectedDevice
            }
            if (device == null) {
                ownerStage?.showAlert(
                    alertTitle = "No Device Selected",
                    alertContent = "Please select an audio device in the Settings tab."
                )
                return
            }
            audioChunker.startListening(device)
        }
        dialogLogger.startSession()
        audioChunker.chunkProcessingEnabled = true
        Platform.runLater {
            listeningButton.text = "Stop dialog"
            listeningButton.style = "-fx-background-color: $COLOUR_BLUE;"
        }
        labelMessagePartVoiceRecognition = "Whisper listening"
        updateLabelMessage()
    }

    private fun setupListeners() {
        audioChunker.onResultListener = { wavFile ->
            labelMessagePartVoiceRecognition = "Whisper process voice"
            updateLabelMessage()
            Platform.runLater {
                val wasAtBottom = isScrolledToBottom()
                val idx = messageBox.children.size
                pendingRowIndexes.addLast(idx)
                messageBox.children.add(
                    createMessageEntry(
                        DialogItem(originalMessage = "...", messageFromAi = "..."),
                    )
                )
                if (wasAtBottom) scrollToBottom()
            }
            val appInfo = getAppInfo()
            whisperUtil.runFasterWhisper(
                wavFile = wavFile,
                language = appInfo.questionLanguage,
            )
        }
        audioChunker.onRecordStart = {
            labelMessagePartRecord = "Audio chunker in record"
            updateLabelMessage()
        }
        audioChunker.onRecordStop = {
            labelMessagePartRecord = ""
            updateLabelMessage()
        }
        whisperUtil.onResultListener = { resultString ->
            labelMessagePartVoiceRecognition = "Whisper result ready"
            updateLabelMessage()
            dialogLogger.logQuestion(resultString)
            Platform.runLater {
                val wasAtBottom = isScrolledToBottom()
                val idx = pendingRowIndexes.removeFirstOrNull() ?: messageBox.children.size
                val entry = createMessageEntry(
                    DialogItem(originalMessage = resultString, messageFromAi = "..."),
                )
                if (idx in 0 until messageBox.children.size) {
                    messageBox.children[idx] = entry
                } else {
                    messageBox.children.add(entry)
                }
                if (wasAtBottom) scrollToBottom()
                processMessage(index = idx, message = resultString)
            }
        }
        whisperUtil.onFailureListener = {
            log.warn("Whisper failed for a chunk, removing placeholder row")
            Platform.runLater {
                val idx = pendingRowIndexes.removeFirstOrNull() ?: return@runLater
                if (idx in 0 until messageBox.children.size) {
                    messageBox.children.removeAt(idx)
                }
            }
        }
    }

    private fun processMessage(index: Int, message: String) {
        if (message.isBlank()) {
            log.error("Translate message is empty")
            return
        }
        ensureSchedulerStarted()
        // Snapshot of recent sources, most-recent first, captured BEFORE adding the current one.
        val previousTexts = recentSources.reversed()
        recentSources.addLast(message)
        while (recentSources.size > 5) recentSources.removeFirst()
        val task = TranslationTask(
            seq = ++translationSeq,
            index = index,
            text = message,
            attempts = 0,
            bornAtMillis = System.currentTimeMillis(),
            previousTexts = previousTexts,
        )
        backgroundThreadScope.launch {
            translationQueueMutex.withLock {
                translationQueue.add(task)
                translationQueue.sortByDescending { it.seq }
                while (translationQueue.size > MAX_TRANSLATION_QUEUE) {
                    markTranslationDropped(translationQueue.removeAt(translationQueue.lastIndex))
                }
            }
            schedulerWakeups.trySend(Unit)
        }
    }

    private data class TranslationTask(
        val seq: Long,
        val index: Int,
        val text: String,
        var attempts: Int,
        val bornAtMillis: Long,
        val previousTexts: List<String>,
    )

    private fun ensureSchedulerStarted() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = backgroundThreadScope.launch { runSchedulerLoop() }
    }

    /** Cancels in-flight translation, drops the queue, and invalidates pending UI writes. */
    private fun clearTranslations() {
        translationEpoch += 1
        schedulerJob?.cancel()
        schedulerJob = null
        backgroundThreadScope.launch {
            translationQueueMutex.withLock { translationQueue.clear() }
        }
    }

    /**
     * Single-consumer scheduler: always picks the newest queued message, honours the selected
     * model's rate limits before dispatching, and processes one translation at a time
     * (non-preemptive — a running request is never cancelled to start a newer one, because
     * cancelling does not reclaim the quota already spent on the in-flight HTTP call).
     */
    private suspend fun runSchedulerLoop() {
        while (true) {
            val task = takeNewestTask()
            if (isStale(task)) {
                markTranslationDropped(task)
                continue
            }
            val appInfo = getAppInfo()
            configureLimiterFor(appInfo.selectedModel)

            val prompt = PromptTemplate.from(appInfo.prompt.prompt)
                .apply(buildPromptVariables(appInfo.prompt.prompt, task))
            val estimatedTokens = prompt.text().length / 4 + 256

            val wait = rateLimiter.tryReserve(estimatedTokens)
            if (wait > 0L) {
                requeueTask(task)
                withTimeoutOrNull(wait.coerceAtMost(SCHEDULER_WAIT_SLICE_MILLIS)) {
                    schedulerWakeups.receive()
                }
                continue
            }
            runTranslation(task, appInfo, prompt)
        }
    }

    /** Supplies only the placeholders the selected prompt actually contains (avoids missing/extra vars). */
    private fun buildPromptVariables(promptText: String, task: TranslationTask): Map<String, Any> {
        val variables = HashMap<String, Any>()
        variables[SOURCE_TEXT_CLEAR] = task.text
        for (i in 1..5) {
            if (promptText.contains("{{previous_text_$i}}")) {
                variables["previous_text_$i"] = task.previousTexts.getOrNull(i - 1).orEmpty()
            }
        }
        return variables
    }

    private suspend fun takeNewestTask(): TranslationTask {
        while (true) {
            val task = translationQueueMutex.withLock {
                if (translationQueue.isEmpty()) null else translationQueue.removeAt(0)
            }
            if (task != null) return task
            schedulerWakeups.receive()
        }
    }

    private suspend fun requeueTask(task: TranslationTask) {
        translationQueueMutex.withLock {
            translationQueue.add(task)
            translationQueue.sortByDescending { it.seq }
            while (translationQueue.size > MAX_TRANSLATION_QUEUE) {
                markTranslationDropped(translationQueue.removeAt(translationQueue.lastIndex))
            }
        }
    }

    private fun isStale(task: TranslationTask): Boolean =
        System.currentTimeMillis() - task.bornAtMillis > TRANSLATION_TTL_MILLIS

    private fun configureLimiterFor(model: LlmModel) {
        val limits = model.googleAiModel
        rateLimiter.configure(
            rpm = limits?.limitRequestsInMinute,
            tpm = limits?.limitTokensInMinute,
            rpd = limits?.limitRequestsInDay,
            tpd = limits?.limitTokensInDay,
        )
    }

    private suspend fun runTranslation(task: TranslationTask, appInfo: ApplicationInfo, prompt: Prompt) {
        val epoch = translationEpoch
        setProcessStatus(
            if (task.attempts == 0) "Process message"
            else "Retry ${task.attempts}/${MAX_TRANSLATION_ATTEMPTS - 1}"
        )
        val flow = when (appInfo.selectedModel.engine) {
            LlmModelEngine.GOOGLE -> cloudRepository.streamGoogleAI(
                model = appInfo.selectedModel.id,
                apiKey = appInfo.googleCloudToken,
                prompt = prompt,
            )
            LlmModelEngine.OPENAI -> cloudRepository.streamOpenAI(
                model = appInfo.selectedModel.id,
                apiKey = appInfo.openAiToken,
                prompt = prompt,
            )
            LlmModelEngine.CLAUDE -> cloudRepository.streamClaude(
                model = appInfo.selectedModel.id,
                apiKey = appInfo.claudeToken,
                prompt = prompt,
            )
        }
        var finalText = ""
        try {
            flow.collect { cumulative ->
                finalText = cumulative
                if (epoch == translationEpoch && appInfo.useStreamingResponse) {
                    updateMessageAt(
                        index = task.index,
                        newItem = DialogItem(
                            originalMessage = task.text,
                            messageFromAi = cumulative.ifBlank { "..." },
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            handleTranslationError(task, e, epoch)
            return
        }
        if (epoch != translationEpoch) return
        val cleaned = finalText.normalizeAndRemoveEmptyLines()
        if (cleaned.isBlank()) {
            log.error("Translate message result is empty")
            updateMessageAt(
                index = task.index,
                newItem = DialogItem(originalMessage = task.text, messageFromAi = "..."),
            )
            setProcessStatus("Process message error, result is empty")
            return
        }
        log.debug("Translate message result size: ${cleaned.length}")
        lastMessageFromAI = cleaned
        dialogLogger.logAnswer(cleaned)
        updateMessageAt(
            index = task.index,
            newItem = DialogItem(originalMessage = task.text, messageFromAi = cleaned),
        )
        setProcessStatus("")
    }

    private fun handleTranslationError(task: TranslationTask, error: Throwable, epoch: Long) {
        log.error("Streaming error: ${error.message}", error)
        val message = error.message ?: ""
        if ("\"code\": 429" in message) rateLimiter.penalize(null)
        val nonRetryable = listOf("\"code\": 400", "\"code\": 401", "\"code\": 403", "\"code\": 404")
            .any { it in message }
        if (!nonRetryable && task.attempts + 1 < MAX_TRANSLATION_ATTEMPTS && !isStale(task)) {
            task.attempts += 1
            val backoff = (1000L shl task.attempts) + (System.nanoTime() and 0xFFL)
            backgroundThreadScope.launch {
                delay(backoff)
                requeueTask(task)
                schedulerWakeups.trySend(Unit)
            }
            setProcessStatus("Retry ${task.attempts}/${MAX_TRANSLATION_ATTEMPTS - 1}")
        } else {
            if (epoch == translationEpoch) {
                updateMessageAt(
                    index = task.index,
                    newItem = DialogItem(originalMessage = task.text, messageFromAi = "..."),
                )
            }
            setProcessStatus("Process message error")
        }
    }

    private fun markTranslationDropped(task: TranslationTask) {
        log.debug("Translation dropped (stale or evicted): index={}", task.index)
    }

    private fun setProcessStatus(status: String) {
        labelMessagePartProcessMessage = status
        updateLabelMessage()
    }

    // ---------- Streaming Whisper recognition ----------

    /** FX-thread: row currently being filled by streaming partials; -1 = none (next partial starts a new row). */
    @Volatile
    private var currentStreamingRow: Int = -1

    @Volatile
    private var currentRecognitionEngine: VoiceRecognitionEngine = VoiceRecognitionEngine.WHISPER_BATCH

    private data class RecognitionConfig(
        val engine: VoiceRecognitionEngine,
        val whisperModelPath: String,
        val language: String,
        val stepMillis: Long,
        val minChars: Int,
        val maxMillis: Long,
        val sherpaModelPath: String,
        val sherpaConfig: SherpaModelConfig,
    )

    init {
        setupStreamingListeners()
        applyRecognitionEngine(getAppInfo())
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow
                .map {
                    RecognitionConfig(
                        engine = it.voiceRecognitionEngine,
                        whisperModelPath = it.whisperModelPath,
                        language = it.questionLanguage,
                        stepMillis = it.whisperModelConfig.streamingStepMilliseconds,
                        minChars = it.whisperModelConfig.streamingMinChars,
                        maxMillis = it.whisperModelConfig.maxChunkDurationMilliseconds,
                        sherpaModelPath = it.sherpaModelPath,
                        sherpaConfig = it.sherpaConfig,
                    )
                }
                .distinctUntilChanged()
                .collect { applyRecognitionEngine(getAppInfo()) }
        }
    }

    private fun setupStreamingListeners() {
        audioChunker.onAudioFrame = { pcm ->
            when (currentRecognitionEngine) {
                VoiceRecognitionEngine.WHISPER_STREAMING -> whisperStreamingUtil.feedAudio(pcm)
                VoiceRecognitionEngine.SHERPA_ONNX -> sherpaRecognizer.feedAudio(pcm)
                VoiceRecognitionEngine.WHISPER_BATCH -> {}
            }
        }
        audioChunker.onUtteranceEnd = {
            whisperStreamingUtil.flush()
        }
        val onPartial: (String) -> Unit = { text ->
            if (text.isNotBlank()) {
                Platform.runLater {
                    val wasAtBottom = isScrolledToBottom()
                    val idx = ensureStreamingRow()
                    messageBox.children[idx] = createMessageEntry(
                        DialogItem(originalMessage = text, messageFromAi = "..."),
                    )
                    if (wasAtBottom) scrollToBottom()
                }
            }
        }
        val onFinal: (String) -> Unit = { text ->
            if (text.isNotBlank()) {
                dialogLogger.logQuestion(text)
                Platform.runLater {
                    val wasAtBottom = isScrolledToBottom()
                    val idx = ensureStreamingRow()
                    messageBox.children[idx] = createMessageEntry(
                        DialogItem(originalMessage = text, messageFromAi = "..."),
                    )
                    currentStreamingRow = -1
                    if (wasAtBottom) scrollToBottom()
                    processMessage(index = idx, message = text)
                }
            }
        }
        whisperStreamingUtil.onPartialListener = onPartial
        whisperStreamingUtil.onFinalListener = onFinal
        sherpaRecognizer.onPartialListener = onPartial
        sherpaRecognizer.onFinalListener = onFinal
    }

    /** Must run on the FX thread. Returns the current streaming row, creating one if needed. */
    private fun ensureStreamingRow(): Int {
        if (currentStreamingRow in 0 until messageBox.children.size) return currentStreamingRow
        currentStreamingRow = messageBox.children.size
        messageBox.children.add(
            createMessageEntry(DialogItem(originalMessage = "...", messageFromAi = "...")),
        )
        return currentStreamingRow
    }

    private fun applyRecognitionEngine(appInfo: ApplicationInfo) {
        currentStreamingRow = -1
        currentRecognitionEngine = appInfo.voiceRecognitionEngine
        when (appInfo.voiceRecognitionEngine) {
            VoiceRecognitionEngine.WHISPER_STREAMING -> {
                sherpaRecognizer.stop()
                audioChunker.streamingFeedsContinuously = false
                audioChunker.streamingModeEnabled = true
                whisperStreamingUtil.start(
                    modelPath = appInfo.whisperModelPath,
                    language = appInfo.questionLanguage,
                    stepMillis = appInfo.whisperModelConfig.streamingStepMilliseconds,
                    minChars = appInfo.whisperModelConfig.streamingMinChars,
                    maxUtteranceMillis = appInfo.whisperModelConfig.maxChunkDurationMilliseconds,
                )
            }
            VoiceRecognitionEngine.SHERPA_ONNX -> {
                whisperStreamingUtil.stop()
                audioChunker.streamingFeedsContinuously = true
                audioChunker.streamingModeEnabled = true
                sherpaRecognizer.start(
                    modelDir = appInfo.sherpaModelPath,
                    numThreads = appInfo.sherpaConfig.numThreads,
                    decodingMethod = appInfo.sherpaConfig.decodingMethod,
                    maxActivePaths = appInfo.sherpaConfig.maxActivePaths,
                    splitMaxWords = appInfo.sherpaConfig.splitMaxWords,
                    maxHangMillis = appInfo.sherpaConfig.maxHangMillis,
                )
            }
            VoiceRecognitionEngine.WHISPER_BATCH -> {
                whisperStreamingUtil.stop()
                sherpaRecognizer.stop()
                audioChunker.streamingFeedsContinuously = false
                audioChunker.streamingModeEnabled = false
            }
        }
    }

    private fun updateMessageAt(index: Int, newItem: DialogItem) {
        Platform.runLater {
            val wasAtBottom = isScrolledToBottom()
            val newEntry = createMessageEntry(newItem)
            if (index in 0 until messageBox.children.size) {
                messageBox.children[index] = newEntry
            } else {
                messageBox.children.add(newEntry)
            }
            if (wasAtBottom) scrollToBottom()
        }
    }


    private fun isScrolledToBottom(): Boolean {
        // If content fits in the viewport there is no scrollbar and vvalue stays at 0 —
        // treat that as "at the bottom" so newly added messages auto-scroll.
        val viewportH = scrollPane.viewportBounds.height
        val contentH = scrollPane.content?.layoutBounds?.height ?: 0.0
        if (contentH <= viewportH + 1.0) return true
        return scrollPane.vvalue >= scrollPane.vmax - 0.02
    }

    private fun scrollToBottom() {
        scrollPane.applyCss()
        scrollPane.layout()
        scrollPane.vvalue = scrollPane.vmax
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
            BorderWidths(1.0),
        )
    )

    private fun updateLabelMessage() {
        AppStatus.statusFlow.value = listOf(
            labelMessagePartVoiceRecognition,
            labelMessagePartProcessMessage,
            labelMessagePartRecord,
        ).filter { it.isNotBlank() }.joinToString(" | ")
    }

}
