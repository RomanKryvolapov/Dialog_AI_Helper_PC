package ui

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import app.DialogApplication.Companion.ownerStage
import backgroundThreadScope
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.lastOrNull
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
import utils.WhisperResult
import utils.WhisperUtil

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlinx.coroutines.withTimeoutOrNull
import javafx.scene.control.Tooltip
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.timeout
import models.domain.ApplicationInfo
import models.domain.ConversationTurn
import models.domain.LlmModel
import models.domain.ModelPrompt
import models.domain.effectiveParameters
import models.domain.TranslationMethod
import utils.ConversationContext
import utils.LastRequests
import utils.QuestionsAssistant
import utils.RateLimiters
import utils.TranslationPrompts
import utils.Failure
import utils.FailureKind
import utils.ActiveMethod
import utils.TokenUsageTracker
import utils.SHORT_WAIT_MILLIS
import utils.classifyFailure
import kotlin.time.Duration.Companion.milliseconds
import utils.TranslationRateLimiter
import utils.VoiceAnswer
import java.io.File
import java.io.IOException

import models.domain.VoiceRecognitionEngine
import utils.WhisperStreamingUtil

import utils.SherpaOnnxRecognizer
import utils.SpeechmaticsRecognizer
import models.domain.SpeechmaticsConfig

import models.domain.SherpaModelConfig


class MessagesTab(
    private val whisperUtil: WhisperUtil,
    private val whisperStreamingUtil: WhisperStreamingUtil,
    private val sherpaRecognizer: SherpaOnnxRecognizer,
    private val speechmaticsRecognizer: SpeechmaticsRecognizer,
    private val audioChunker: AudioChunker,
    private val cloudRepository: CloudRepository,
    private val dialogLogger: DialogLogger,
    private val preferencesRepository: PreferencesRepository,
    private val activeMethod: ActiveMethod,
    private val tokenUsageTracker: TokenUsageTracker,
    private val lastRequests: LastRequests,
    private val rateLimiters: RateLimiters,
    private val questionsAssistant: QuestionsAssistant,
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    private val log = LoggerFactory.getLogger("MessagesTab")

    companion object {
        const val FIELD_BACKGROUND_COLOUR = "#323232"
        const val BUTTONS_BACKGROUND_COLOUR = "#323232"
        /** The gap between the tabs, which styles.css leaves as each tab's right background inset. */
        const val HEADER_SPACING = 10.0

        private const val MAX_TRANSLATION_QUEUE = 8
        /** Attempts per phrase in all, across the methods the dialog moves through; on the last method, with pauses. */
        private const val MAX_TRANSLATION_ATTEMPTS = 3
        private const val TRANSLATION_TTL_MILLIS = 90_000L
        private const val SCHEDULER_WAIT_SLICE_MILLIS = 1_000L
        private const val SUMMARY_TIMEOUT_MILLIS = 60_000L
        /**
         * An answer that has not started, or has stopped arriving, for this long counts as a failure and
         * the phrase moves on to the next method. The HTTP timeout only covers the wait for the response
         * headers, so a stream that stalls after them would otherwise hold its phrase — and, for a voice
         * method, every phrase after it — indefinitely.
         */
        private const val STALL_TIMEOUT_MILLIS = 30_000L
        private const val SKIPPED_TEXT = "(skipped — no translation method could take it in time)"

        // Bounded-concurrency dispatch: several translations run in parallel, but the shared
        // rate limiter is the real guard that keeps us within the model's quota. The pool size is
        // derived from the model's requests-per-minute (see concurrencyFor); these are just bounds.
        private const val MIN_CONCURRENT_TRANSLATIONS = 2
        private const val MAX_CONCURRENT_TRANSLATIONS = 8
        private const val DEFAULT_CONCURRENT_TRANSLATIONS = 6
        private const val ASSUMED_REQUEST_SECONDS = 6.0
    }

    val content: Pane
    val scrollPane: ScrollPane

    @Volatile
    private var lastMessageFromAI = "..."

    /** One limiter per model id: Google counts quotas per model, so switching model gets its own allowance. */

    /** Voice phrases go one at a time, in the order they were said: each prompt needs the transcript before it. */
    private val voiceSlot = Semaphore(1)

    private val translationQueueMutex = Mutex()
    private val translationQueue = ArrayList<TranslationTask>()
    private val schedulerWakeups = Channel<Unit>(Channel.CONFLATED)
    private var schedulerJob: Job? = null

    /** Limits how many text translations run at once; replaced when the primary method's limits change. */
    @Volatile
    private var dispatchSemaphore = Semaphore(DEFAULT_CONCURRENT_TRANSLATIONS)
    @Volatile
    private var maxConcurrency = DEFAULT_CONCURRENT_TRANSLATIONS

    private var translationSeq = 0L

    /** The conversation so far, as the prompts see it: a running summary plus the latest turns. */
    private val conversationContext = ConversationContext()

    /** Bumped on "Clear dialog" so in-flight results from a previous screen are ignored. */
    @Volatile
    private var translationEpoch = 0L


    private var labelMessagePartVoiceRecognition = ""
    private var labelMessagePartProcessMessage = ""
    private var labelMessagePartRecord = ""

    private val listeningButton = Button("Start dialog").apply {
        style = "-fx-background-color: $COLOUR_GREEN;"
        setOnAction { toggleDialog() }
    }

    private val clearButton = Button("Clear dialog").apply {
        style = "-fx-background-color: $COLOUR_RED;"
        setOnAction {
            messageBox.children.clear()
            lastMessageFromAI = "..."
            clearTranslations()
        }
    }

    /**
     * The dialog's own buttons: the window shows them in its top bar, right after the tabs, sized and
     * spaced like them (the `header-button` style).
     */
    val headerControls: HBox = HBox(HEADER_SPACING, listeningButton, clearButton).apply {
        alignment = Pos.CENTER_LEFT
        listOf(listeningButton, clearButton).forEach { it.styleClass.add("header-button") }
    }

    /** What the live loop is doing: recognition, translation, which method answers and why. */
    private val statusLabel = Label().apply {
        styleClass.add("status-label")
        maxWidth = Double.MAX_VALUE
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

        val statusBar = HBox(statusLabel).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(0.0, 12.0, 0.0, 12.0)
            background = Background(BackgroundFill(Color.web(BUTTONS_BACKGROUND_COLOUR), null, null))
            HBox.setHgrow(statusLabel, Priority.ALWAYS)
        }

        content = BorderPane().apply {
            center = scrollPane
            bottom = statusBar
            background = Background(BackgroundFill(Color.web("#2b2b2b"), null, null))
        }

        mainThreadScope.launch {
            AppStatus.statusFlow.collect { status ->
                statusLabel.text = status
                statusLabel.tooltip = status.takeIf { it.isNotBlank() }?.let { Tooltip(it) }
            }
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

        // A changed key or chain starts over from method 1.
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow
                .map { info ->
                    listOf(info.googleCloudToken, info.openAiToken, info.claudeToken) +
                        info.translationMethods.map { it.key }
                }
                .distinctUntilChanged()
                .drop(1)
                .collect { activeMethod.reset() }
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
        val chain = appInfo.translationMethods
        // A new dialog starts on method 1; one that cannot work is a failure like any other: the next takes over.
        activeMethod.reset()
        val skipped = ArrayList<String>()
        for (method in chain) {
            val problem = startProblem(method, appInfo) ?: break
            skipped += "${method.shortTitle}: $problem"
            activeMethod.failed(chain, method, problem)
        }
        val primary = activeMethod.current(chain)
        startProblem(primary, appInfo)?.let { problem ->
            ownerStage?.showAlert(
                alertTitle = "${primary.title} cannot start",
                alertContent = "$problem. No method of the chain can work: fix it in the Settings tab, or choose other translation methods in Settings → General.",
            )
            return
        }
        if (!audioChunker.inRunning()) {
            val devices = audioChunker.getAvailableInputDevices()
            val device = devices.firstOrNull { it.name == appInfo.lastSelectedDevice }
                ?: devices.firstOrNull()?.also {
                    log.info("No audio device chosen yet, falling back to ${it.name}")
                }
            if (device == null) {
                ownerStage?.showAlert(
                    alertTitle = "No Audio Device",
                    alertContent = "No audio input device was found."
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
        labelMessagePartVoiceRecognition = "Listening — ${primary.title}"
        labelMessagePartProcessMessage = skipped.joinToString("; ") { "skipped $it" }
        updateLabelMessage()
    }

    /**
     * Why [method] cannot work, or null if it can: its provider's key is missing, or its recogniser has no model.
     * Each method takes the audio in its own way once it is in use, so it needs nothing from the others.
     */
    private fun startProblem(method: TranslationMethod, appInfo: ApplicationInfo): String? {
        if (apiKey(method.model, appInfo).isBlank()) return "no ${method.model.engine.type} API key"
        return method.recognizer?.let { recognizerProblem(it, appInfo) }
    }

    private fun recognizerProblem(recognizer: VoiceRecognitionEngine, appInfo: ApplicationInfo): String? = when (recognizer) {
        VoiceRecognitionEngine.WHISPER_BATCH, VoiceRecognitionEngine.WHISPER_STREAMING ->
            "no Whisper model is set".takeIf { appInfo.whisperModelPath.isBlank() }
        VoiceRecognitionEngine.SHERPA_ONNX -> "no Sherpa-ONNX model is set".takeIf { appInfo.sherpaModelPath.isBlank() }
        VoiceRecognitionEngine.SPEECHMATICS -> "no Speechmatics API key".takeIf { appInfo.speechmaticsToken.isBlank() }
    }

    private fun apiKey(model: LlmModel, appInfo: ApplicationInfo): String = when (model.engine) {
        LlmModelEngine.GOOGLE -> appInfo.googleCloudToken
        LlmModelEngine.OPENAI -> appInfo.openAiToken
        LlmModelEngine.CLAUDE -> appInfo.claudeToken
    }

    private fun setupListeners() {
        audioChunker.onResultListener = { wavFile ->
            if (currentRecognizer == null) onVoicePhrase(wavFile) else onWhisperPhrase(wavFile)
        }
        audioChunker.onRecordStart = {
            labelMessagePartRecord = "Audio chunker in record"
            updateLabelMessage()
        }
        audioChunker.onRecordStop = {
            labelMessagePartRecord = ""
            updateLabelMessage()
        }
    }

    /**
     * Whisper (batch) recognises the phrase for a text method 1: its row is added at once, and the result of
     * this phrase's own Whisper run fills that row, however the runs of other phrases overlap it. When a
     * voice method is among the reserves, the recording is kept so that method can hear it if method 1 fails.
     */
    private fun onWhisperPhrase(wavFile: File) {
        val appInfo = getAppInfo()
        val audio = if (appInfo.translationMethods.any { it.voice }) {
            try {
                PhraseAudio(wavFile.readBytes())
            } catch (e: IOException) {
                log.warn("Phrase recording could not be kept for a voice reserve: ${e.message}")
                null
            }
        } else null
        labelMessagePartVoiceRecognition = "Whisper process voice"
        updateLabelMessage()
        Platform.runLater {
            val wasAtBottom = isScrolledToBottom()
            val index = messageBox.children.size
            messageBox.children.add(createMessageEntry(DialogItem(originalMessage = "...", messageFromAi = "...")))
            if (wasAtBottom) scrollToBottom()
            val epoch = translationEpoch
            val recordedFor = activeMethod.current(appInfo.translationMethods)
            val dialog = activeMethod.flow.value.dialog
            val added = beginTurn("")
            backgroundThreadScope.launch {
                val result = whisperUtil.recognize(wavFile, appInfo.questionLanguage)
                // After Clear dialog the row and the turn belong to a screen and a conversation that are gone.
                Platform.runLater { if (epoch == translationEpoch) onWhisperResult(index, added, result, audio, recordedFor, dialog) }
            }
        }
    }

    /** FX thread: settles the row and the turn of one phrase Whisper (batch) has finished with. */
    private fun onWhisperResult(
        index: Int,
        added: ConversationContext.Added,
        result: WhisperResult,
        audio: PhraseAudio?,
        recordedFor: TranslationMethod,
        dialog: Long,
    ) {
        when (result) {
            is WhisperResult.Heard -> {
                labelMessagePartVoiceRecognition = "Whisper result ready"
                updateLabelMessage()
                dialogLogger.logQuestion(result.text)
                val wasAtBottom = isScrolledToBottom()
                if (index in 0 until messageBox.children.size) {
                    messageBox.children[index] = createMessageEntry(DialogItem(originalMessage = result.text, messageFromAi = "..."))
                }
                if (wasAtBottom) scrollToBottom()
                added.turn.source = result.text
                phraseHeard(added.turn)
                enqueueTranslation(index, added, audio)
            }
            WhisperResult.Failed -> {
                switchAfterFailure(getAppInfo().translationMethods, recordedFor, "Whisper could not transcribe a phrase", dialog)
                if (audio != null) {
                    // The recording is kept: the method now in use takes the phrase, a voice one hears it itself.
                    enqueueTranslation(index, added, audio, failedMethod = recordedFor, dialog = dialog)
                } else {
                    log.warn("Whisper failed for a phrase, hiding its row")
                    conversationContext.discard(added.turn)
                    hideRow(index)
                }
            }
            WhisperResult.Silence -> {
                log.debug("Whisper heard nothing in a phrase, hiding its row")
                conversationContext.discard(added.turn)
                hideRow(index)
            }
        }
    }

    /**
     * Hidden rather than removed: later phrases' rows are addressed by index, and removing one would shift
     * every row after it, so their translations would land in the wrong rows.
     */
    private fun hideRow(index: Int) {
        messageBox.children.getOrNull(index)?.apply {
            isVisible = false
            isManaged = false
        }
    }

    /** A phrase whose text (or recording, [audio]) is ready: it joins the conversation and the translation queue. */
    private fun processMessage(index: Int, message: String, audio: PhraseAudio? = null, speaker: String? = null) {
        if (audio == null && message.isBlank()) {
            log.error("Translate message is empty")
            return
        }
        val added = beginTurn(message)
        added.turn.speaker = speaker
        if (message.isNotBlank()) phraseHeard(added.turn)
        enqueueTranslation(index, added, audio)
    }

    /**
     * The phrase's turn in the conversation, taken now — for a Whisper (batch) phrase when its row appears,
     * before its text is known, so turns keep the order the phrases were said in however the Whisper runs
     * finish. A turn with no text yet stays out of every prompt and every summary until it gets one.
     */
    private fun beginTurn(source: String): ConversationContext.Added {
        val appInfo = getAppInfo()
        val canSummarize = appInfo.translationMethods.any { it.model.readsText && apiKey(it.model, appInfo).isNotBlank() }
        val added = conversationContext.add(source, TranslationPrompts.contextSettings(appInfo.prompts, canSummarize))
        added.fold?.let { foldConversation(it, appInfo) }
        return added
    }

    /** [failedMethod]: the method that already failed this phrase (Whisper could not hear it for method 1). */
    private fun enqueueTranslation(
        index: Int,
        added: ConversationContext.Added,
        audio: PhraseAudio?,
        failedMethod: TranslationMethod? = null,
        dialog: Long? = null,
    ) {
        ensureSchedulerStarted()
        val task = TranslationTask(
            seq = ++translationSeq,
            index = index,
            turn = added.turn,
            context = added.before,
            attempts = 0,
            bornAtMillis = System.currentTimeMillis(),
            epoch = translationEpoch,
            audio = audio,
            dialog = dialog ?: activeMethod.flow.value.dialog,
        )
        if (failedMethod != null) {
            task.attempts = 1
            task.failures += "${failedMethod.shortTitle}: ${FailureKind.TRANSCRIPTION.reason}"
        }
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

    /**
     * A voice primary method: the recorded phrase itself goes to the model, which transcribes and
     * translates it. The chunk is read into memory and its file deleted at once, so retries, Clear dialog
     * and dropped phrases leave nothing behind on disk.
     */

    private fun onVoicePhrase(wavFile: File) {
        val wav = try {
            wavFile.readBytes()
        } catch (e: IOException) {
            log.error("Voice phrase could not be read: ${e.message}", e)
            return
        } finally {
            if (!wavFile.delete()) log.warn("Failed to delete chunk ${wavFile.name}")
        }
        labelMessagePartVoiceRecognition = "${activeMethod.current(getAppInfo().translationMethods).model.title} hears the phrase"
        updateLabelMessage()
        Platform.runLater {
            val wasAtBottom = isScrolledToBottom()
            val idx = messageBox.children.size
            messageBox.children.add(createMessageEntry(DialogItem(originalMessage = "...", messageFromAi = "...")))
            if (wasAtBottom) scrollToBottom()
            processMessage(index = idx, message = "", audio = PhraseAudio(wav))
        }
    }

    private data class TranslationTask(
        val seq: Long,
        val index: Int,
        val turn: ConversationTurn,
        /** The conversation as it stood before this phrase. */
        val context: ConversationContext.Snapshot,
        /** Failed attempts so far, on any method. */
        var attempts: Int,
        val bornAtMillis: Long,
        /** The screen the phrase belongs to; after Clear dialog a late retry of it is dropped, not shown. */
        val epoch: Long,
        /** The recorded phrase, when it was cut at pauses and a voice method may hear it. */
        val audio: PhraseAudio? = null,
        /** The dialog (start) it belongs to: a failure from an ended dialog switches nothing in the next. */
        val dialog: Long = 0,
        /** Why each method could not translate it, for the row if none can. */
        val failures: MutableList<String> = ArrayList(),
        /** A voice answer that arrived in the wrong shape, shown as it is if no other method does better. */
        var rawAnswer: String? = null,
    ) {
        val text: String get() = turn.source

        /**
         * Recorded for a voice primary method and not transcribed yet: its transcript can only come from a
         * model, and the next phrase's prompt needs it, so such phrases go oldest first, one at a time.
         */
        val awaitsTranscript: Boolean get() = audio != null && turn.source.isEmpty()
    }

    /** One recorded phrase, 16 kHz 16-bit mono WAV (32,000 bytes a second). */
    private class PhraseAudio(val wav: ByteArray)

    private fun ensureSchedulerStarted() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = backgroundThreadScope.launch { runSchedulerLoop() }
    }

    /**
     * Cancels in-flight translation, drops the queue, invalidates pending UI writes and starts a new
     * conversation, its token count from zero.
     */
    private fun clearTranslations() {
        translationEpoch += 1
        tokenUsageTracker.reset()
        schedulerJob?.cancel()
        schedulerJob = null
        conversationContext.clear()
        lastRequests.summaryIs("")
        questionsAssistant.clear()
        backgroundThreadScope.launch {
            translationQueueMutex.withLock { translationQueue.clear() }
        }
    }

    /**
     * Dispatcher loop: picks the next queued phrase — the newest text phrase, or the oldest phrase waiting
     * for a voice method's transcript (see [takeNextTask]) — and sends it to the method in use (see
     * [ActiveMethod]), whose rate limiter then counts the request; a wait over [SHORT_WAIT_MILLIS] is a failure
     * of that method like any other. Up to
     * [maxConcurrency] text translations run in parallel, voice phrases one at a time. Each translation runs
     * in its own coroutine so a slow or hung request never blocks the others.
     */
    private suspend fun runSchedulerLoop() {
        while (true) {
            val task = takeNextTask()
            if (task.epoch != translationEpoch) continue // queued before Clear dialog
            if (isStale(task)) {
                markTranslationDropped(task)
                continue
            }
            val appInfo = getAppInfo()
            val chain = appInfo.translationMethods
            configureDispatchPool(activeMethod.current(chain).model)

            // Take the slot before choosing: a phrase heard by a voice method waits here for the one before
            // it, and its prompt then carries that phrase's transcript.
            val sem = if (task.awaitsTranscript) voiceSlot else dispatchSemaphore
            sem.acquire()
            if (task.awaitsTranscript && olderPhraseAwaitsTranscript(task)) {
                // The phrase before this one failed and was put back while this one waited for the slot:
                // it goes first, so this one's prompt can carry its transcript.
                sem.release()
                requeueTask(task)
                continue
            }
            val method = activeMethod.current(chain)
            startProblem(method, appInfo)?.let { problem ->
                if (switchAfterFailure(chain, method, problem, task.dialog)) {
                    sem.release()
                    requeueTask(task)
                    continue
                }
            }
            unusableReason(method, task, appInfo)?.let { reason ->
                sem.release()
                failTranslation(task, task.failures + "${method.shortTitle}: $reason")
                continue
            }
            val wait = limiterFor(method.model).tryReserve(estimateTokens(method, task, appInfo))
            if (wait > SHORT_WAIT_MILLIS) {
                sem.release()
                // Its own rate limit would hold the phrase back: for live speech that is a failure like any other.
                val busy = "its rate limit would hold the phrase for ${wait / 1000} s"
                if (switchAfterFailure(chain, method, busy, task.dialog)) {
                    requeueTask(task)
                    continue
                }
                // The last method has nowhere to hand over: the phrase waits for it, if it is still worth showing.
                if (wait > task.bornAtMillis + TRANSLATION_TTL_MILLIS - System.currentTimeMillis()) {
                    failTranslation(task, task.failures + "${method.shortTitle}: $busy")
                    continue
                }
                setProcessStatus("Waiting: ${method.shortTitle} — $busy")
                requeueTask(task)
                withTimeoutOrNull(wait.coerceAtMost(SCHEDULER_WAIT_SLICE_MILLIS)) { schedulerWakeups.receive() }
                continue
            }
            if (wait > 0L) {
                sem.release()
                requeueTask(task)
                withTimeoutOrNull(wait) { schedulerWakeups.receive() }
                continue
            }
            backgroundThreadScope.launch {
                try {
                    runTranslation(task, appInfo, method)
                } finally {
                    sem.release()
                    schedulerWakeups.trySend(Unit)
                }
            }
        }
    }

    private suspend fun olderPhraseAwaitsTranscript(task: TranslationTask): Boolean =
        translationQueueMutex.withLock { translationQueue.any { it.awaitsTranscript && it.seq < task.seq } }

    /** Why [method] cannot take this particular phrase, or null if it can. */
    private fun unusableReason(method: TranslationMethod, task: TranslationTask, appInfo: ApplicationInfo): String? =
        when {
            apiKey(method.model, appInfo).isBlank() -> "no ${method.model.engine.type} API key"
            method.voice && task.audio == null -> "hears only phrases recorded at pauses (Whisper batch or a voice primary)"
            !method.voice && task.text.isBlank() && appInfo.whisperModelPath.isBlank() ->
                "no Whisper model to transcribe the phrase"
            else -> null
        }

    /**
     * The request [method] gets for [task]. Built when it is sent, not when the phrase arrived, so it carries
     * the transcripts and translations of the earlier phrases that came in meanwhile.
     */
    private fun promptFor(method: TranslationMethod, task: TranslationTask, appInfo: ApplicationInfo): ModelPrompt =
        if (method.voice) TranslationPrompts.voice(appInfo.prompts, task.context)
        else TranslationPrompts.text(appInfo.prompts, task.context, task.text)

    private fun estimateTokens(method: TranslationMethod, task: TranslationTask, appInfo: ApplicationInfo): Int {
        val prompt = promptFor(method, task, appInfo)
        // Gemini counts 32 tokens per second of audio (OpenAI 10), and a second of a chunk is 32,000 bytes.
        val audioTokens = if (method.voice) task.audio?.wav?.size?.div(1000) ?: 0 else 0
        return estimateTextTokens(prompt) + audioTokens
    }

    private fun estimateTextTokens(prompt: ModelPrompt): Int = (prompt.system.length + prompt.user.length) / 4 + 256

    /**
     * Folds the phrases that left the window of previous phrases into the running summary: one background
     * request through the summary model's rate limiter, like the translations. On failure they wait for the
     * next try (see [ConversationContext.foldDone]).
     */

    private fun foldConversation(fold: ConversationContext.Fold, appInfo: ApplicationInfo) {
        val model = summaryModel(appInfo)
        if (model == null) {
            log.warn("No method in the chain can write a summary now, the phrases wait for the next try")
            conversationContext.foldDone(fold, null)
            return
        }
        backgroundThreadScope.launch {
            var summary: String? = null
            val limiter = limiterFor(model)
            try {
                val prompt = TranslationPrompts.summary(appInfo.prompts, fold)
                val estimatedTokens = estimateTextTokens(prompt)
                // Bounded, waiting for quota included: a request that never ends would block every
                // later summary of this conversation.
                summary = withTimeoutOrNull(SUMMARY_TIMEOUT_MILLIS) {
                    while (true) {
                        val wait = limiter.tryReserve(estimatedTokens)
                        if (wait == 0L) break
                        delay(wait.coerceAtMost(SCHEDULER_WAIT_SLICE_MILLIS))
                    }
                    lastRequests.summarySent(LastRequests.Sent(model.title, prompt))
                    streamText(model, appInfo, prompt).lastOrNull()
                }
                    ?.normalizeAndRemoveEmptyLines()
                    ?.takeIf { it.isNotBlank() }
                if (summary == null) {
                    log.warn("Conversation summary timed out or came back empty, the phrases wait for the next try")
                } else {
                    log.info("Conversation summary updated by {}, {} phrases folded in:\n{}", model.id, fold.turns.size, summary)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failure = classifyFailure(e)
                limiter.note(failure)
                log.warn("Conversation summary failed ({}), the phrases wait for the next try: {}", failure.kind, failure.detail)
            } finally {
                conversationContext.foldDone(fold, summary)
                lastRequests.summaryIs(conversationContext.currentSummary)
            }
        }
    }

    /**
     * The model that writes the conversation summary: the method in use's, when it reads text and has its key (so
     * the summary costs what the translations cost), else the first method of the chain that does.
     */
    private fun summaryModel(appInfo: ApplicationInfo): LlmModel? {
        val chain = appInfo.translationMethods
        return (listOf(activeMethod.current(chain)) + chain)
            .firstOrNull { it.model.readsText && apiKey(it.model, appInfo).isNotBlank() }?.model
    }

    /**
     * [method] failed for [reason]: when it is the method in use, the next of [chain] takes over until the dialog
     * restarts, and the status says so. True when the method in use changed.
     */
    private fun switchAfterFailure(
        chain: List<TranslationMethod>,
        method: TranslationMethod,
        reason: String,
        dialog: Long = activeMethod.flow.value.dialog,
    ): Boolean {
        val switched = activeMethod.failed(chain, method, reason, dialog)
        if (switched) {
            val now = activeMethod.current(chain)
            log.warn("{} failed ({}): {} takes over until the dialog is started again", method.title, reason, now.title)
            setProcessStatus("${method.shortTitle}: $reason — switched to ${now.shortTitle} until the dialog is started again")
        }
        return switched
    }

    private fun streamFor(method: TranslationMethod, appInfo: ApplicationInfo, prompt: ModelPrompt, audio: PhraseAudio?): Flow<String> {
        if (!method.voice) return streamText(method.model, appInfo, prompt)
        val wav = checkNotNull(audio) { "a voice method needs the recorded phrase" }.wav
        val model = method.model
        val parameters = parametersFor(model, appInfo)
        return when (model.engine) {
            LlmModelEngine.GOOGLE -> cloudRepository.streamGoogleAIWithAudio(model.id, appInfo.googleCloudToken, prompt, parameters, wav)
            LlmModelEngine.OPENAI -> cloudRepository.streamOpenAIWithAudio(model.id, appInfo.openAiToken, prompt, parameters, wav)
            LlmModelEngine.CLAUDE -> error("${model.id} does not hear audio")
        }
    }

    private fun streamText(model: LlmModel, appInfo: ApplicationInfo, prompt: ModelPrompt): Flow<String> {
        val parameters = parametersFor(model, appInfo)
        return when (model.engine) {
            LlmModelEngine.GOOGLE -> cloudRepository.streamGoogleAI(model.id, appInfo.googleCloudToken, prompt, parameters)
            LlmModelEngine.OPENAI -> cloudRepository.streamOpenAI(model.id, appInfo.openAiToken, prompt, parameters)
            LlmModelEngine.CLAUDE -> cloudRepository.streamClaude(model.id, appInfo.claudeToken, prompt, parameters)
        }
    }

    /** The user's settings for [model] (Settings → LLM), narrowed to what it accepts. */
    private fun parametersFor(model: LlmModel, appInfo: ApplicationInfo) =
        effectiveParameters(model.parameterSupport, appInfo.modelParameters[model.id])

    /**
     * The next phrase to translate. Text phrases go newest first, so a backlog never delays what is
     * being said now. Phrases waiting for a voice method go oldest first: each one's prompt needs the
     * transcript of the phrase before it, which only the model can produce.
     */

    private suspend fun takeNextTask(): TranslationTask {
        while (true) {
            val task = translationQueueMutex.withLock {
                val oldestVoice = translationQueue.filter { it.awaitsTranscript }.minByOrNull { it.seq }
                when {
                    oldestVoice != null -> oldestVoice.also { translationQueue.remove(it) }
                    translationQueue.isEmpty() -> null
                    else -> translationQueue.removeAt(0)
                }
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

    /** [turn] has its text now: with the Questions mode on, it is checked for a question too. */
    private fun phraseHeard(turn: ConversationTurn) = questionsAssistant.phraseHeard(turn, conversationContext.currentSummary)

    /** [model]'s rate limiter, shared with every other request to it (see [RateLimiters]). */
    private fun limiterFor(model: LlmModel): TranslationRateLimiter = rateLimiters.forModel(model)

    /** Sizes the text dispatch pool from the primary method's requests-per-minute. */
    private fun configureDispatchPool(primary: LlmModel) {
        val desired = concurrencyFor(primary.limitRequestsInMinute)
        if (desired != maxConcurrency) {
            maxConcurrency = desired
            dispatchSemaphore = Semaphore(desired)
        }
    }

    /**
     * Auto-derives the size of the parallel dispatch pool from the model's requests-per-minute:
     * roughly how many requests can be in flight at once given a typical request duration, clamped
     * to sane bounds. The rate limiter still enforces the actual RPM/TPM quota; this is only a cap.
     */
    private fun concurrencyFor(rpm: Int?): Int =
        if (rpm == null) DEFAULT_CONCURRENT_TRANSLATIONS
        else ceil(rpm * ASSUMED_REQUEST_SECONDS / 60.0).toInt()
            .coerceIn(MIN_CONCURRENT_TRANSLATIONS, MAX_CONCURRENT_TRANSLATIONS)

    @OptIn(FlowPreview::class)
    private suspend fun runTranslation(task: TranslationTask, appInfo: ApplicationInfo, method: TranslationMethod) {
        val epoch = task.epoch
        val limiter = limiterFor(method.model)
        val position = appInfo.translationMethods.indexOfFirst { it.key == method.key }
        setProcessStatus(
            when {
                position > 0 -> "Reserve $position: ${method.title}"
                task.attempts > 0 -> "Retry ${task.attempts}/${MAX_TRANSLATION_ATTEMPTS - 1}"
                else -> "Process message"
            }
        )
        if (!method.voice && task.text.isBlank()) {
            // Recorded for a voice method that could not translate it: Whisper hears it for this text method.
            val heard = try {
                task.audio?.wav?.let { whisperUtil.transcribe(it, appInfo.questionLanguage) } ?: WhisperResult.Failed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Escaping here would cancel the whole background scope, the scheduler with it.
                log.warn("Whisper could not transcribe a recorded phrase: ${e.message}", e)
                WhisperResult.Failed
            }
            if (epoch != translationEpoch) return
            when (heard) {
                is WhisperResult.Heard -> {}
                // Nothing was said: like a voice model's "no speech", an answer and not a failure of the method.
                WhisperResult.Silence -> {
                    conversationContext.discard(task.turn)
                    updateMessageAt(index = task.index, newItem = DialogItem(originalMessage = "(no speech)", messageFromAi = "—"))
                    return
                }
                WhisperResult.Failed -> {
                    onMethodFailed(task, method, limiter, Failure(FailureKind.TRANSCRIPTION, "Whisper could not transcribe the phrase"))
                    return
                }
            }
            val text = (heard as WhisperResult.Heard).text
            task.turn.source = text
            phraseHeard(task.turn)
            dialogLogger.logQuestion(text)
            updateMessageAt(index = task.index, newItem = DialogItem(originalMessage = text, messageFromAi = "..."))
        }
        val prompt = promptFor(method, task, appInfo)
        val sent = LastRequests.Sent(method.model.title, prompt)
        if (method.voice) lastRequests.voiceSent(sent) else lastRequests.textSent(sent)
        var finalText = ""
        try {
            streamFor(method, appInfo, prompt, task.audio)
                .timeout(STALL_TIMEOUT_MILLIS.milliseconds)
                .collect { cumulative ->
                    finalText = cumulative
                    if (epoch == translationEpoch && appInfo.useStreamingResponse) {
                        updateMessageAt(index = task.index, newItem = shownWhileStreaming(task, method, cumulative))
                    }
                }
        } catch (e: TimeoutCancellationException) {
            onMethodFailed(task, method, limiter, Failure(FailureKind.TIMEOUT, "nothing arrived for ${STALL_TIMEOUT_MILLIS / 1000} s"))
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log.error("Streaming error from ${method.title}: ${e.message}", e)
            onMethodFailed(task, method, limiter, classifyFailure(e))
            return
        }
        if (epoch != translationEpoch) return
        if (method.voice) finishVoiceAnswer(task, method, limiter, appInfo, finalText)
        else finishTextAnswer(task, method, limiter, appInfo, finalText)
    }

    private suspend fun finishTextAnswer(
        task: TranslationTask,
        method: TranslationMethod,
        limiter: TranslationRateLimiter,
        appInfo: ApplicationInfo,
        answer: String,
    ) {
        val cleaned = answer.normalizeAndRemoveEmptyLines()
        if (cleaned.isBlank()) {
            onMethodFailed(task, method, limiter, Failure(FailureKind.BAD_ANSWER, "empty answer"))
            return
        }
        log.debug("Translate message result size: ${cleaned.length}")
        noteSuccess(method, appInfo)
        lastMessageFromAI = cleaned
        task.turn.translation = cleaned
        dialogLogger.logAnswer(cleaned)
        updateMessageAt(
            index = task.index,
            newItem = DialogItem(originalMessage = task.text, messageFromAi = cleaned),
        )
    }

    /** [method] translated a phrase: the status says when a reserve did it. */
    private fun noteSuccess(method: TranslationMethod, appInfo: ApplicationInfo) {
        val position = appInfo.translationMethods.indexOfFirst { it.key == method.key }
        setProcessStatus(if (position > 0) "Reserve $position in use: ${method.title}" else "")
    }

    private fun shownWhileStreaming(task: TranslationTask, method: TranslationMethod, answer: String): DialogItem {
        if (!method.voice) {
            return DialogItem(originalMessage = task.text, messageFromAi = answer.ifBlank { "..." })
        }
        val parsed = VoiceAnswer.parse(answer)
        return DialogItem(
            originalMessage = parsed.said?.ifBlank { null } ?: "...",
            messageFromAi = parsed.translated?.ifBlank { null } ?: "...",
        )
    }

    /**
     * Settles a voice phrase: its transcript and translation join the conversation; a phrase the model
     * heard no speech in is kept out of it; an answer not in the agreed two-tag shape is a failure, and the
     * phrase moves on to the next method.
     */

    private suspend fun finishVoiceAnswer(
        task: TranslationTask,
        method: TranslationMethod,
        limiter: TranslationRateLimiter,
        appInfo: ApplicationInfo,
        answer: String,
    ) {
        val parsed = VoiceAnswer.parse(answer)
        val said = parsed.said.orEmpty()
        val translated = parsed.translated.orEmpty()
        when {
            said.isNotBlank() && translated.isNotBlank() -> {
                noteSuccess(method, appInfo)
                // A phrase Whisper already transcribed is in the dialog log once, under that transcript.
                if (task.text.isBlank()) dialogLogger.logQuestion(said)
                task.turn.source = said
                task.turn.translation = translated
                phraseHeard(task.turn)
                lastMessageFromAI = translated
                dialogLogger.logAnswer(translated)
                updateMessageAt(index = task.index, newItem = DialogItem(originalMessage = said, messageFromAi = translated))
            }
            parsed.said != null && said.isBlank() -> {
                // The model heard no speech: an answer, not a failure.
                noteSuccess(method, appInfo)
                conversationContext.discard(task.turn)
                updateMessageAt(index = task.index, newItem = DialogItem(originalMessage = "(no speech)", messageFromAi = "—"))
            }
            else -> {
                log.warn("Voice answer from {} not in the expected format: {}", method.title, answer.take(300))
                task.rawAnswer = answer
                onMethodFailed(task, method, limiter, Failure(FailureKind.BAD_ANSWER, "answer not in the expected format"))
            }
        }
    }

    /**
     * [method] could not translate [task]: the dialog moves on to the next method for good (see [ActiveMethod]), and
     * the phrase goes back to the queue for it at once — or after a short pause when the method in use is still the
     * same one, the last of the chain. After [MAX_TRANSLATION_ATTEMPTS] attempts, or once it is too old to show, its
     * row says why it was not translated.
     *
     * Runs inside the failed translation, before it gives back its dispatch slot: a phrase waiting for a
     * voice transcript is back in the queue before the next phrase can take the slot, so the next one
     * cannot overtake it (see [runSchedulerLoop]).
     */
    private suspend fun onMethodFailed(
        task: TranslationTask,
        method: TranslationMethod,
        limiter: TranslationRateLimiter,
        failure: Failure,
    ) {
        log.warn("{} failed a phrase ({}): {}", method.title, failure.kind, failure.detail)
        limiter.note(failure)
        task.attempts += 1
        task.failures += "${method.shortTitle}: ${failure.kind.reason}"
        val chain = getAppInfo().translationMethods
        switchAfterFailure(chain, method, failure.kind.reason, task.dialog)
        if (task.epoch != translationEpoch) return
        if (task.attempts >= MAX_TRANSLATION_ATTEMPTS || isStale(task)) {
            failTranslation(task, task.failures)
            return
        }
        // Straight on to the method now in use; a pause only when it is the same one again (the last of the chain).
        val pause = if (activeMethod.current(chain).key == method.key) (1000L shl task.attempts) + (System.nanoTime() and 0xFFL) else 0L
        if (pause == 0L || task.awaitsTranscript) {
            // A phrase waiting for a voice transcript keeps its slot through the pause, so the one after
            // it cannot go first.
            if (pause > 0L) delay(pause)
            requeueTask(task)
            schedulerWakeups.trySend(Unit)
        } else {
            // A text phrase gives its dispatch slot back for the pause.
            backgroundThreadScope.launch {
                delay(pause)
                requeueTask(task)
                schedulerWakeups.trySend(Unit)
            }
        }
    }

    /** What a rate-limit failure tells the model's limiter: slow down, or stop until the quota is back. */
    private fun TranslationRateLimiter.note(failure: Failure) {
        when (failure.kind) {
            FailureKind.RATE_LIMIT -> penalize(failure.retryAfterMillis)
            FailureKind.DAILY_QUOTA -> failure.retryAfterMillis?.let(::penalize) ?: exhaustDay()
            else -> {}
        }
    }

    /** No method could translate [task]: its row says so and why. */
    private fun failTranslation(task: TranslationTask, reasons: List<String>) {
        // A phrase recorded for a voice method and never transcribed has no text to keep in the conversation.
        if (task.awaitsTranscript) conversationContext.discard(task.turn)
        if (task.epoch != translationEpoch) return
        val why = reasons.distinct().joinToString("; ").ifBlank { "no translation method could take it" }
        log.warn("Phrase not translated: {}", why)
        val raw = task.rawAnswer
        val item = if (raw != null) {
            val parsed = VoiceAnswer.parse(raw)
            DialogItem(
                originalMessage = parsed.said?.ifBlank { null } ?: task.text.ifBlank { "..." },
                messageFromAi = parsed.translated?.ifBlank { null } ?: raw.normalizeAndRemoveEmptyLines().ifBlank { "..." },
            )
        } else {
            DialogItem(originalMessage = task.text.ifBlank { "..." }, messageFromAi = "(not translated — $why)")
        }
        updateMessageAt(index = task.index, newItem = item)
        setProcessStatus("Not translated: $why")
    }

    /** A phrase no method could get to in time (queue full or waited too long): say so on its row. */

    private fun markTranslationDropped(task: TranslationTask) {
        log.info("Phrase skipped, no method could take it in time: index={}", task.index)
        if (task.awaitsTranscript) conversationContext.discard(task.turn)
        if (task.epoch != translationEpoch) return
        updateMessageAt(
            index = task.index,
            newItem = DialogItem(originalMessage = task.text.ifBlank { "..." }, messageFromAi = SKIPPED_TEXT),
        )
        setProcessStatus("Some phrases skipped: speech is faster than the translation methods' rate limits")
    }

    private fun setProcessStatus(status: String) {
        labelMessagePartProcessMessage = status
        updateLabelMessage()
    }

    // ---------- Streaming Whisper recognition ----------

    /** FX-thread: row currently being filled by streaming partials; -1 = none (next partial starts a new row). */
    @Volatile
    private var currentStreamingRow: Int = -1

    /** The recogniser in use; null while the primary method is a voice one, which hears the phrases itself. */
    @Volatile
    private var currentRecognizer: VoiceRecognitionEngine? = VoiceRecognitionEngine.WHISPER_BATCH

    private data class RecognitionConfig(
        /** Method 1's recogniser; null for a voice method. */
        val recognizer: VoiceRecognitionEngine?,
        val whisperModelPath: String,
        val language: String,
        val stepMillis: Long,
        val minChars: Int,
        val maxMillis: Long,
        val sherpaModelPath: String,
        val sherpaConfig: SherpaModelConfig,
        val speechmaticsKey: String,
        val speechmaticsConfig: SpeechmaticsConfig,
    )

    init {
        setupStreamingListeners()
        applyRecognitionEngine(getAppInfo())
        backgroundThreadScope.launch {
            // The method in use changes on a failure as well as in the settings: either rewires the audio input.
            combine(preferencesRepository.appInfoFlow, activeMethod.flow) { info, _ -> info }
                .map {
                    RecognitionConfig(
                        recognizer = activeMethod.current(it.translationMethods).recognizer,
                        whisperModelPath = it.whisperModelPath,
                        language = it.questionLanguage,
                        stepMillis = it.whisperModelConfig.streamingStepMilliseconds,
                        minChars = it.whisperModelConfig.streamingMinChars,
                        maxMillis = it.whisperModelConfig.maxChunkDurationMilliseconds,
                        sherpaModelPath = it.sherpaModelPath,
                        sherpaConfig = it.sherpaConfig,
                        speechmaticsKey = it.speechmaticsToken,
                        speechmaticsConfig = it.speechmaticsConfig,
                    )
                }
                .distinctUntilChanged()
                .collect { applyRecognitionEngine(getAppInfo()) }
        }
    }

    private fun setupStreamingListeners() {
        audioChunker.onAudioFrame = { pcm ->
            when (currentRecognizer) {
                VoiceRecognitionEngine.WHISPER_STREAMING -> whisperStreamingUtil.feedAudio(pcm)
                VoiceRecognitionEngine.SHERPA_ONNX -> sherpaRecognizer.feedAudio(pcm)
                VoiceRecognitionEngine.SPEECHMATICS -> speechmaticsRecognizer.feedAudio(pcm)
                VoiceRecognitionEngine.WHISPER_BATCH, null -> {}
            }
        }
        audioChunker.onUtteranceEnd = {
            if (currentRecognizer == VoiceRecognitionEngine.SPEECHMATICS) speechmaticsRecognizer.flush() else whisperStreamingUtil.flush()
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
        val onFinal: (String, String?) -> Unit = { text, speaker ->
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
                    processMessage(index = idx, message = text, speaker = speaker)
                }
            } else {
                // Nothing was said after all (Whisper made the phrase up on silence): the row its partials opened goes.
                Platform.runLater {
                    if (currentStreamingRow in 0 until messageBox.children.size) hideRow(currentStreamingRow)
                    currentStreamingRow = -1
                }
            }
        }
        whisperStreamingUtil.onPartialListener = onPartial
        whisperStreamingUtil.onFinalListener = { onFinal(it, null) }
        sherpaRecognizer.onPartialListener = onPartial
        sherpaRecognizer.onFinalListener = { onFinal(it, null) }
        speechmaticsRecognizer.onPartialListener = onPartial
        speechmaticsRecognizer.onFinalListener = onFinal
        speechmaticsRecognizer.onFailureListener = { recognizerFailed(VoiceRecognitionEngine.SPEECHMATICS, it) }
        whisperStreamingUtil.onFailureListener = { recognizerFailed(VoiceRecognitionEngine.WHISPER_STREAMING, it) }
        sherpaRecognizer.onFailureListener = { recognizerFailed(VoiceRecognitionEngine.SHERPA_ONNX, it) }
    }

    /** The recogniser of the method in use stopped working during a dialog: a failure of that method like any other. */
    private fun recognizerFailed(recognizer: VoiceRecognitionEngine, reason: String) {
        Platform.runLater {
            val chain = getAppInfo().translationMethods
            val method = activeMethod.current(chain)
            if (method.recognizer == recognizer && audioChunker.chunkProcessingEnabled) switchAfterFailure(chain, method, reason)
        }
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
        val recognizer = activeMethod.current(appInfo.translationMethods).recognizer
        currentRecognizer = recognizer
        when (recognizer) {
            VoiceRecognitionEngine.WHISPER_STREAMING -> {
                sherpaRecognizer.stop()
                speechmaticsRecognizer.stop()
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
                speechmaticsRecognizer.stop()
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
            // Like Whisper streaming: audio goes out only while someone speaks (the service bills what it hears), and a
            // pause settles the phrase (onUtteranceEnd → flush).
            VoiceRecognitionEngine.SPEECHMATICS -> {
                whisperStreamingUtil.stop()
                sherpaRecognizer.stop()
                audioChunker.streamingFeedsContinuously = false
                audioChunker.streamingModeEnabled = true
                speechmaticsRecognizer.start(appInfo.speechmaticsToken, appInfo.questionLanguage, appInfo.speechmaticsConfig)
            }
            // A voice primary method cuts phrases exactly like Whisper batch does; only who hears them differs.
            VoiceRecognitionEngine.WHISPER_BATCH, null -> {
                whisperStreamingUtil.stop()
                sherpaRecognizer.stop()
                speechmaticsRecognizer.stop()
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
