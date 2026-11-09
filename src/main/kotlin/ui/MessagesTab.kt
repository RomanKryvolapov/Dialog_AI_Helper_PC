package ui

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import CONVERSATION_SUMMARY
import CONVERSATION_SUMMARY_CLEAR
import PROMPT_CONVERSATION_SUMMARY
import PROMPT_TRANSLATE_VOICE
import RECENT_TURNS
import RECENT_TURNS_CLEAR
import SOURCE_TEXT
import SOURCE_TEXT_CLEAR
import SUMMARY_TURNS_CLEAR
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

import dev.langchain4j.model.input.Prompt
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
import models.domain.TranslationMethod
import utils.ConversationContext
import utils.Failure
import utils.FailureKind
import utils.MethodChoice
import utils.MethodHealth
import utils.TokenUsageTracker
import utils.SHORT_WAIT_MILLIS
import utils.chooseMethod
import utils.classifyFailure
import kotlin.time.Duration.Companion.milliseconds
import utils.TranslationRateLimiter
import utils.VoiceAnswer
import java.io.File
import java.io.IOException

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
    private val methodHealth: MethodHealth,
    private val tokenUsageTracker: TokenUsageTracker,
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
        /** Attempts per phrase in all: one per method of a full chain; a shorter chain starts over from the top. */
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
        private const val RATE_LIMIT_SAFETY_MARGIN = 0.8
    }

    val content: Pane
    val scrollPane: ScrollPane

    @Volatile
    private var lastMessageFromAI = "..."

    /** One limiter per model id: Google counts quotas per model, so switching model gets its own allowance. */
    private val rateLimiters = HashMap<String, TranslationRateLimiter>()

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

        // A changed key or chain gives every method a fresh chance: a rejected key rests until then.
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow
                .map { info ->
                    listOf(info.googleCloudToken, info.openAiToken, info.claudeToken) +
                        info.translationMethods.map { it.key }
                }
                .distinctUntilChanged()
                .drop(1)
                .collect { methodHealth.reset() }
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
        val primary = appInfo.primaryMethod
        startProblem(primary, appInfo)?.let { problem ->
            ownerStage?.showAlert(
                alertTitle = "${primary.title} cannot start",
                alertContent = "$problem. Fix it in the Settings tab, or choose another translation method in Settings → General.",
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
        // A reserve that cannot work is not a reason to refuse the dialog, but it is said up front.
        labelMessagePartProcessMessage = appInfo.translationMethods.drop(1)
            .mapNotNull { method -> startProblem(method, appInfo)?.let { "Reserve ${method.shortTitle} will be skipped: $it" } }
            .joinToString("; ")
        updateLabelMessage()
    }

    /**
     * Why [method] cannot work in the chain as configured, or null if it can: its provider's key is
     * missing, or the input it needs cannot be produced — a recogniser model for a text method, a recording
     * cut at pauses for a voice one.
     */
    private fun startProblem(method: TranslationMethod, appInfo: ApplicationInfo): String? {
        if (apiKey(method.model, appInfo).isBlank()) return "no ${method.model.engine.type} API key"
        // Method 1 takes the audio in; the others work on what it produced.
        val intake = appInfo.primaryMethod.recognizer
        return when {
            method.voice && intake != null && !intake.recordsPhrases ->
                "a voice method hears phrases recorded at pauses, which ${intake.title} does not keep"
            !method.voice && intake == null && appInfo.whisperModelPath.isBlank() ->
                "after a voice method, Whisper transcribes the phrase for a text method, and no Whisper model is set"
            !method.voice && intake != null -> recognizerProblem(intake, appInfo)
            else -> null
        }
    }

    private fun recognizerProblem(recognizer: VoiceRecognitionEngine, appInfo: ApplicationInfo): String? = when (recognizer) {
        VoiceRecognitionEngine.WHISPER_BATCH, VoiceRecognitionEngine.WHISPER_STREAMING ->
            "no Whisper model is set".takeIf { appInfo.whisperModelPath.isBlank() }
        VoiceRecognitionEngine.SHERPA_ONNX -> "no Sherpa-ONNX model is set".takeIf { appInfo.sherpaModelPath.isBlank() }
        VoiceRecognitionEngine.SPEECHMATICS -> null
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
            val added = beginTurn("")
            backgroundThreadScope.launch {
                val result = whisperUtil.recognize(wavFile, appInfo.questionLanguage)
                // After Clear dialog the row and the turn belong to a screen and a conversation that are gone.
                Platform.runLater { if (epoch == translationEpoch) onWhisperResult(index, added, result, audio) }
            }
        }
    }

    /** FX thread: settles the row and the turn of one phrase Whisper (batch) has finished with. */
    private fun onWhisperResult(index: Int, added: ConversationContext.Added, result: WhisperResult, audio: PhraseAudio?) {
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
                enqueueTranslation(index, added, audio)
            }
            WhisperResult.Failed -> if (audio != null) {
                // Method 1 could not hear it, but the recording is kept for a voice method further down the
                // chain: the phrase goes on to it like after any other failure of method 1, and that method's
                // transcript fills the turn.
                log.warn("Whisper failed for a phrase, handing the recording to the next method")
                setProcessStatus("Whisper failed — the phrase goes to the next method")
                enqueueTranslation(index, added, audio, failedMethod = getAppInfo().primaryMethod)
            } else {
                log.warn("Whisper failed for a phrase, hiding its row")
                conversationContext.discard(added.turn)
                hideRow(index)
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
    private fun processMessage(index: Int, message: String, audio: PhraseAudio? = null) {
        if (audio == null && message.isBlank()) {
            log.error("Translate message is empty")
            return
        }
        enqueueTranslation(index, beginTurn(message), audio)
    }

    /**
     * The phrase's turn in the conversation, taken now — for a Whisper (batch) phrase when its row appears,
     * before its text is known, so turns keep the order the phrases were said in however the Whisper runs
     * finish. A turn with no text yet stays out of every prompt and every summary until it gets one.
     */
    private fun beginTurn(source: String): ConversationContext.Added {
        val appInfo = getAppInfo()
        val added = conversationContext.add(
            source = source,
            // The voice prompt always carries the running notes, so a chain with a voice method keeps them.
            summarize = appInfo.translationMethods.any { it.voice } ||
                appInfo.prompt.prompt.contains(CONVERSATION_SUMMARY),
        )
        added.fold?.let { foldConversation(it, appInfo) }
        return added
    }

    /** [failedMethod]: the method that already failed this phrase (Whisper could not hear it for method 1). */
    private fun enqueueTranslation(
        index: Int,
        added: ConversationContext.Added,
        audio: PhraseAudio?,
        failedMethod: TranslationMethod? = null,
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
        )
        if (failedMethod != null) {
            task.tried += failedMethod.key
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
        labelMessagePartVoiceRecognition = "${getAppInfo().primaryMethod.model.title} hears the phrase"
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
        /** Methods that failed this phrase in the current pass along the chain. */
        val tried: MutableSet<String> = HashSet(),
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
        backgroundThreadScope.launch {
            translationQueueMutex.withLock { translationQueue.clear() }
        }
    }

    /**
     * Dispatcher loop: picks the next queued phrase — the newest text phrase, or the oldest phrase waiting
     * for a voice method's transcript (see [takeNextTask]) — and the method for it: the first in the chain
     * that can take it now (see [chooseMethod]), whose rate limiter then counts the request. Up to
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
            configureDispatchPool(appInfo.primaryMethod.model)

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
            val choice = try {
                chooseWithRetryPass(task, chain, appInfo)
            } catch (e: CancellationException) {
                sem.release()
                throw e
            } catch (e: Exception) {
                // Most likely a prompt edited into one the template engine refuses. This phrase cannot be
                // prepared; the loop goes on, and without the slot it took.
                sem.release()
                log.error("A phrase could not be prepared: ${e.message}", e)
                failTranslation(task, task.failures + "its request could not be prepared: ${e.message}")
                continue
            }
            when (choice) {
                is MethodChoice.Use -> backgroundThreadScope.launch {
                    try {
                        runTranslation(task, appInfo, choice.method)
                    } finally {
                        sem.release()
                        schedulerWakeups.trySend(Unit)
                    }
                }
                is MethodChoice.NoneLeft -> {
                    sem.release()
                    failTranslation(task, task.failures + choice.reasons)
                }
                is MethodChoice.Wait -> {
                    sem.release()
                    // Compared as a remainder: a rest "until the settings change" is Long.MAX_VALUE long.
                    if (choice.millis > task.bornAtMillis + TRANSLATION_TTL_MILLIS - System.currentTimeMillis()) {
                        // Nothing in the chain is free before the phrase would be too old to show.
                        failTranslation(task, task.failures + choice.reasons)
                        continue
                    }
                    if (choice.millis > SHORT_WAIT_MILLIS) setProcessStatus("Waiting: ${choice.reasons.joinToString("; ")}")
                    requeueTask(task)
                    withTimeoutOrNull(choice.millis.coerceAtMost(SCHEDULER_WAIT_SLICE_MILLIS)) {
                        schedulerWakeups.receive()
                    }
                }
            }
        }
    }

    /** [choose], and when every method has failed the phrase once, another pass from the top of the chain. */
    private fun chooseWithRetryPass(task: TranslationTask, chain: List<TranslationMethod>, appInfo: ApplicationInfo): MethodChoice {
        val choice = choose(task, chain, appInfo)
        if (choice !is MethodChoice.NoneLeft || task.tried.isEmpty() || task.attempts >= MAX_TRANSLATION_ATTEMPTS) return choice
        task.tried.clear()
        return choose(task, chain, appInfo)
    }

    private suspend fun olderPhraseAwaitsTranscript(task: TranslationTask): Boolean =
        translationQueueMutex.withLock { translationQueue.any { it.awaitsTranscript && it.seq < task.seq } }

    /** The method for [task] now: see [chooseMethod]. Counts the request against that method's rate limit. */
    private fun choose(task: TranslationTask, chain: List<TranslationMethod>, appInfo: ApplicationInfo): MethodChoice =
        chooseMethod(
            chain = chain,
            skip = task.tried,
            now = System.currentTimeMillis(),
            unusableReason = { unusableReason(it, task, appInfo) },
            rest = { methodHealth.restOf(it.key) },
            reserve = { limiterFor(it.model).tryReserve(estimateTokens(it, task, appInfo)) },
        )

    /** Why [method] cannot take this particular phrase, or null if it can. */
    private fun unusableReason(method: TranslationMethod, task: TranslationTask, appInfo: ApplicationInfo): String? =
        when {
            apiKey(method.model, appInfo).isBlank() -> "no ${method.model.engine.type} API key"
            method.voice && task.audio == null -> "hears only phrases recorded at pauses (Whisper batch or a voice primary)"
            !method.voice && task.text.isBlank() && appInfo.whisperModelPath.isBlank() ->
                "no Whisper model to transcribe the phrase"
            else -> null
        }

    private fun templateFor(method: TranslationMethod, appInfo: ApplicationInfo): String =
        if (method.voice) PROMPT_TRANSLATE_VOICE else appInfo.prompt.prompt

    private fun estimateTokens(method: TranslationMethod, task: TranslationTask, appInfo: ApplicationInfo): Int {
        val template = templateFor(method, appInfo)
        val prompt = PromptTemplate.from(template).apply(buildPromptVariables(template, task))
        // Gemini counts 32 tokens per second of audio (OpenAI 10), and a second of a chunk is 32,000 bytes.
        val audioTokens = if (method.voice) task.audio?.wav?.size?.div(1000) ?: 0 else 0
        return prompt.text().length / 4 + 256 + audioTokens
    }

    /** Supplies only the placeholders the selected prompt actually contains (avoids missing/extra vars). */
    private fun buildPromptVariables(promptText: String, task: TranslationTask): Map<String, Any> {
        val variables = HashMap<String, Any>()
        if (promptText.contains(SOURCE_TEXT)) {
            variables[SOURCE_TEXT_CLEAR] = task.text
        }
        val earlier = task.context.turns
        for (i in 1..5) {
            if (promptText.contains("{{previous_text_$i}}")) {
                variables["previous_text_$i"] = ConversationContext.previousText(earlier, i)
            }
        }
        if (promptText.contains(CONVERSATION_SUMMARY)) {
            variables[CONVERSATION_SUMMARY_CLEAR] = task.context.summary
        }
        if (promptText.contains(RECENT_TURNS)) {
            variables[RECENT_TURNS_CLEAR] = ConversationContext.renderTurns(earlier)
        }
        return variables
    }

    private fun usesConversation(promptText: String): Boolean =
        promptText.contains(RECENT_TURNS) || promptText.contains(CONVERSATION_SUMMARY)

    /**
     * Folds the turns that left the recent window into the running summary: one background request
     * through the summary model's rate limiter, like the translations. On failure the turns stay verbatim.
     */

    private fun foldConversation(fold: ConversationContext.Fold, appInfo: ApplicationInfo) {
        val model = summaryModel(appInfo)
        if (model == null) {
            log.warn("No method in the chain can write a summary now, keeping the turns verbatim")
            conversationContext.foldDone(fold, null)
            return
        }
        backgroundThreadScope.launch {
            var summary: String? = null
            val limiter = limiterFor(model)
            try {
                val prompt = PromptTemplate.from(PROMPT_CONVERSATION_SUMMARY).apply(
                    mapOf(
                        CONVERSATION_SUMMARY_CLEAR to fold.summary,
                        SUMMARY_TURNS_CLEAR to ConversationContext.renderTurns(fold.turns),
                    )
                )
                val estimatedTokens = prompt.text().length / 4 + 256
                // Bounded, waiting for quota included: a request that never ends would block every
                // later summary of this conversation.
                summary = withTimeoutOrNull(SUMMARY_TIMEOUT_MILLIS) {
                    while (true) {
                        val wait = limiter.tryReserve(estimatedTokens)
                        if (wait == 0L) break
                        delay(wait.coerceAtMost(SCHEDULER_WAIT_SLICE_MILLIS))
                    }
                    streamText(model, appInfo, prompt).lastOrNull()
                }
                    ?.normalizeAndRemoveEmptyLines()
                    ?.takeIf { it.isNotBlank() }
                if (summary == null) {
                    log.warn("Conversation summary timed out or came back empty, keeping the turns verbatim")
                } else {
                    log.info("Conversation summary updated by {}, {} turns folded:\n{}", model.id, fold.turns.size, summary)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failure = classifyFailure(e)
                limiter.note(failure)
                log.warn("Conversation summary failed ({}), keeping the turns verbatim: {}", failure.kind, failure.detail)
            } finally {
                conversationContext.foldDone(fold, summary)
            }
        }
    }

    /**
     * The model that writes the conversation notes: the first in the chain that answers text, has its key
     * and is not resting — the primary's when it can, so the notes cost what the translations cost.
     */
    private fun summaryModel(appInfo: ApplicationInfo): LlmModel? =
        appInfo.translationMethods.firstOrNull { method ->
            method.model.readsText && apiKey(method.model, appInfo).isNotBlank() && methodHealth.restOf(method.key) == null
        }?.model

    private fun streamFor(method: TranslationMethod, appInfo: ApplicationInfo, prompt: Prompt, audio: PhraseAudio?): Flow<String> {
        if (!method.voice) return streamText(method.model, appInfo, prompt)
        val wav = checkNotNull(audio) { "a voice method needs the recorded phrase" }.wav
        val model = method.model
        return when (model.engine) {
            LlmModelEngine.GOOGLE -> cloudRepository.streamGoogleAIWithAudio(model.id, appInfo.googleCloudToken, prompt, wav)
            LlmModelEngine.OPENAI -> cloudRepository.streamOpenAIWithAudio(model.id, appInfo.openAiToken, prompt, wav)
            LlmModelEngine.CLAUDE -> error("${model.id} does not hear audio")
        }
    }

    private fun streamText(model: LlmModel, appInfo: ApplicationInfo, prompt: Prompt) = when (model.engine) {
        LlmModelEngine.GOOGLE -> cloudRepository.streamGoogleAI(
            model = model.id,
            apiKey = appInfo.googleCloudToken,
            prompt = prompt,
        )
        LlmModelEngine.OPENAI -> cloudRepository.streamOpenAI(
            model = model.id,
            apiKey = appInfo.openAiToken,
            prompt = prompt,
        )
        LlmModelEngine.CLAUDE -> cloudRepository.streamClaude(
            model = model.id,
            apiKey = appInfo.claudeToken,
            prompt = prompt,
        )
    }

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

    /** [model]'s own rate limiter, set to its limits: Google counts quotas per model, so each gets its own. */

    private fun limiterFor(model: LlmModel): TranslationRateLimiter {
        val limiter = synchronized(rateLimiters) { rateLimiters.getOrPut(model.id) { TranslationRateLimiter() } }
        limiter.configure(
            rpm = model.limitRequestsInMinute?.let { (it * RATE_LIMIT_SAFETY_MARGIN).toInt().coerceAtLeast(1) },
            tpm = model.limitTokensInMinute?.let { (it * RATE_LIMIT_SAFETY_MARGIN).toInt().coerceAtLeast(1) },
            rpd = model.limitRequestsInDay,
            tpd = model.limitTokensInDay,
        )
        return limiter
    }

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
                task.audio?.wav?.let { whisperUtil.transcribe(it, appInfo.questionLanguage) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Escaping here would cancel the whole background scope, the scheduler with it.
                log.warn("Whisper could not transcribe a recorded phrase: ${e.message}", e)
                null
            }
            if (epoch != translationEpoch) return
            if (heard.isNullOrBlank()) {
                onMethodFailed(task, method, limiter, Failure(FailureKind.TRANSCRIPTION, "Whisper returned no text"))
                return
            }
            task.turn.source = heard
            dialogLogger.logQuestion(heard)
            updateMessageAt(index = task.index, newItem = DialogItem(originalMessage = heard, messageFromAi = "..."))
        }
        val template = templateFor(method, appInfo)
        val prompt = try {
            PromptTemplate.from(template).apply(buildPromptVariables(template, task))
        } catch (e: Exception) {
            // A prompt edited into one the template engine refuses: no method can be asked with it.
            log.error("The prompt could not be built: ${e.message}", e)
            failTranslation(task, task.failures + "its request could not be prepared: ${e.message}")
            return
        }
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
        // Only a conversation prompt's answer is a translation in that conversation; another prompt's
        // (an answer to a question, say) would mislead later phrases if it were fed back as one.
        if (usesConversation(appInfo.prompt.prompt)) task.turn.translation = cleaned
        dialogLogger.logAnswer(cleaned)
        updateMessageAt(
            index = task.index,
            newItem = DialogItem(originalMessage = task.text, messageFromAi = cleaned),
        )
    }

    /** [method] translated a phrase: it is healthy again, and the status says when a reserve did it. */
    private fun noteSuccess(method: TranslationMethod, appInfo: ApplicationInfo) {
        methodHealth.recordSuccess(method.key)
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
     * [method] could not translate [task]: the method rests as [failure] calls for (see [MethodHealth]),
     * and the phrase goes back to the queue for the next method in the chain — at once, or after a short
     * pause when every method has failed it and the chain starts over. After [MAX_TRANSLATION_ATTEMPTS]
     * attempts, or once it is too old to show, its row says why it was not translated.
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
        val rest = methodHealth.recordFailure(method.key, failure)
        task.tried += method.key
        task.attempts += 1
        task.failures += "${method.shortTitle}: ${rest?.describe() ?: failure.kind.reason}"
        if (task.epoch != translationEpoch) return
        if (task.attempts >= MAX_TRANSLATION_ATTEMPTS || isStale(task)) {
            failTranslation(task, task.failures)
            return
        }
        setProcessStatus("${method.shortTitle}: ${failure.kind.reason} — trying the next method")
        // A pause only when every method has failed the phrase and the chain starts over.
        val pause = if (getAppInfo().translationMethods.all { it.key in task.tried }) {
            (1000L shl task.attempts) + (System.nanoTime() and 0xFFL)
        } else 0L
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
    )

    init {
        setupStreamingListeners()
        applyRecognitionEngine(getAppInfo())
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow
                .map {
                    RecognitionConfig(
                        recognizer = it.primaryMethod.recognizer,
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
            when (currentRecognizer) {
                VoiceRecognitionEngine.WHISPER_STREAMING -> whisperStreamingUtil.feedAudio(pcm)
                VoiceRecognitionEngine.SHERPA_ONNX -> sherpaRecognizer.feedAudio(pcm)
                VoiceRecognitionEngine.WHISPER_BATCH, VoiceRecognitionEngine.SPEECHMATICS, null -> {}
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
        val recognizer = appInfo.primaryMethod.recognizer
        currentRecognizer = recognizer
        when (recognizer) {
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
            VoiceRecognitionEngine.SPEECHMATICS -> {
                whisperStreamingUtil.stop()
                sherpaRecognizer.stop()
                audioChunker.streamingFeedsContinuously = false
                audioChunker.streamingModeEnabled = false
                log.warn("Speechmatics engine selected, but the realtime client is not wired yet — no transcription will occur")
            }
            // A voice primary method cuts phrases exactly like Whisper batch does; only who hears them differs.
            VoiceRecognitionEngine.WHISPER_BATCH, null -> {
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
