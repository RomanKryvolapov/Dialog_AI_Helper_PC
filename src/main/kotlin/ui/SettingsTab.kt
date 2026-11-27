package ui

import app.DialogApplication.Companion.ownerStage
import extensions.actionButton
import extensions.addButtons
import extensions.addCheckBox
import extensions.addComboBox
import extensions.addSection
import extensions.addTextField
import extensions.chooseDirectory
import extensions.copyButton
import extensions.noteLabel
import extensions.pasteButton
import extensions.saveButton
import extensions.settingsPage
import extensions.settingsTextField
import extensions.showError
import extensions.showAlert
import extensions.showNote
import extensions.statusLabel
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.input.KeyCode
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.ApplicationInfo
import models.domain.FloatingPanelMode
import models.domain.HotkeyAction
import javafx.scene.control.Label
import models.domain.WindowSettings
import utils.Hotkey
import models.domain.SherpaModelConfig
import models.domain.SpeechmaticsConfig
import models.domain.WhisperModelConfig
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.AudioChunker
import java.awt.Desktop
import java.net.URI
import javax.sound.sampled.Mixer

class SettingsTab(
    private val audioChunker: AudioChunker,
    private val mainPromptTab: MainPromptTab,
    private val questionsPromptTab: QuestionsPromptTab,
    private val voicePromptTab: VoicePromptTab,
    private val modelParametersPane: ModelParametersPane,
    private val consoleTab: ConsoleTab,
    private val methodChainSelector: MethodChainSelector,
    private val windowPresence: WindowPresence,
    preferencesRepository: PreferencesRepository,
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
        private const val SIDEBAR_BACKGROUND_COLOUR = "#2b2b2b"
        private const val CHAIN_MAX_WIDTH = 1200.0
        private const val CHAIN_ROW_PADDING = 20.0
        private const val HOTKEY_TITLE_WIDTH = 320.0

        /**
         * Why [keys] (blank: none) cannot be saved: a text that is no combination, or one combination given to two
         * actions. Null when they can.
         */
        internal fun hotkeysProblem(keys: Map<HotkeyAction, String>): String? {
            val parsed = keys.filterValues { it.isNotBlank() }.mapValues { (action, text) ->
                Hotkey.parse(text) ?: return "${action.title}: press a modifier (Ctrl, Alt, Shift, Win) together with a key"
            }
            val twice = parsed.entries.groupBy({ it.value.text }, { it.key }).entries.firstOrNull { it.value.size > 1 }
                ?: return null
            return "${twice.key} is given to both ${twice.value[0].title} and ${twice.value[1].title}"
        }
    }

    private val log = LoggerFactory.getLogger("SettingsTab")

    private val detailContainer = StackPane().apply {
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))
    }

    val content: BorderPane = BorderPane().apply {
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        // The sidebar lists them in this order.
        val pages = listOf(
            "General" to buildGeneralPane(),
            "System" to buildSystemPane(),
            "Phrases" to buildPhrasesPane(),
            "LLM" to scrollOf(modelParametersPane.content),
            "API keys" to buildApiKeysPane(),
            "Whisper" to buildWhisperPane(),
            "Sherpa-ONNX" to buildSherpaPane(),
            "Speechmatics" to buildSpeechmaticsPane(),
            "Main prompt" to scrollOf(mainPromptTab.content),
            "Voice prompt" to scrollOf(voicePromptTab.content),
            "Questions" to scrollOf(questionsPromptTab.content),
            "Console" to wrapNode(consoleTab.content),
        )
        val group = ToggleGroup()
        val categoryButtons = pages.map { (title, page) -> categoryButton(title, group) { showCategory(page) } }

        left = VBox(8.0, *categoryButtons.toTypedArray()).apply {
            padding = Insets(20.0, 12.0, 20.0, 12.0)
            prefWidth = 240.0
            minWidth = 240.0
            background = Background(BackgroundFill(Color.web(SIDEBAR_BACKGROUND_COLOUR), null, null))
        }
        center = detailContainer

        categoryButtons.first().isSelected = true
        showCategory(pages.first().second)
    }

    private fun categoryButton(title: String, group: ToggleGroup, onShow: () -> Unit): ToggleButton {
        return ToggleButton(title).apply {
            toggleGroup = group
            maxWidth = Double.MAX_VALUE
            styleClass.add("settings-category-button")
            setOnAction {
                isSelected = true
                onShow()
            }
        }
    }

    private fun showCategory(node: Node) {
        detailContainer.children.setAll(node)
    }

    private fun scrollOf(box: VBox): ScrollPane {
        return ScrollPane(box).apply {
            isFitToWidth = true
            isPannable = true
            style = "-fx-background: $WINDOW_BACKGROUND_COLOUR; -fx-background-color: $WINDOW_BACKGROUND_COLOUR;"
        }
    }

    private fun wrapNode(node: Node): ScrollPane {
        return ScrollPane(node).apply {
            isFitToWidth = true
            isFitToHeight = true
            isPannable = true
            style = "-fx-background: $WINDOW_BACKGROUND_COLOUR; -fx-background-color: $WINDOW_BACKGROUND_COLOUR;"
        }
    }

    private fun buildGeneralPane(): ScrollPane = scrollOf(settingsPage {
        val appInfo = getAppInfo()

        addSection(
            title = "Translation method",
            note = "Method 1 translates until it fails; then method 2 takes over for the rest of the dialog, and method 3 " +
                "after it if that fails too (stop and start the dialog to begin with method 1 again). Each method is how " +
                "the speech is heard → which model translates (only a speech recogniser needs one).",
        ) {
            children.add(leftAligned(methodChainSelector.node))
        }

        addSection(
            title = "Audio device",
            note = "Where the speech comes from: a microphone, or a virtual cable that carries the call or the conference.",
        ) {
            val devices: List<Mixer.Info> = audioChunker.getAvailableInputDevices()
            if (devices.isEmpty()) {
                children.add(noteLabel("Not found"))
            } else {
                // Not saved when it falls back to the first device: that would turn a stand-in into
                // a stored choice and stop `.env` (or a device plugged in later) from ever applying.
                val lastSelected = devices.firstOrNull { appInfo.lastSelectedDevice == it.name }
                addComboBox(
                    items = devices,
                    selectedItem = lastSelected ?: devices.first(),
                    toStringFn = { mixerInfo -> mixerInfo.name },
                    onSelected = { mixerInfo -> saveAppInfo(getAppInfo().copy(lastSelectedDevice = mixerInfo.name)) },
                )
            }
        }

        addSection(
            title = "Speech language",
            note = "The language being spoken, as a code: en, ru, de… The speech recognisers (Whisper, Speechmatics) listen for it.",
        ) {
            val field = addTextField(appInfo.questionLanguage)
            val status = statusLabel(field)
            addButtons(
                saveButton {
                    saveAppInfo(getAppInfo().copy(questionLanguage = field.text))
                    status.showNote("Saved")
                },
                status,
            )
        }

        addSection(
            title = "Interface size",
            note = "How large everything in the window is drawn. A smaller size fits more on a small screen. " +
                "A window too narrow for the top bar at the chosen size is drawn smaller, so that nothing is cut off.",
        ) {
            addComboBox(
                items = ApplicationInfo.UI_SCALE_PERCENTS,
                selectedItem = appInfo.uiScalePercent,
                toStringFn = { percent -> "$percent%" },
                onSelected = { percent -> saveAppInfo(getAppInfo().copy(uiScalePercent = percent)) },
                short = true,
            )
        }
    })

    private fun buildSystemPane(): ScrollPane = scrollOf(settingsPage {
        val window = getAppInfo().window
        fun save(change: (WindowSettings) -> WindowSettings) =
            saveAppInfo(getAppInfo().let { it.copy(window = change(it.window)) })

        addSection(
            title = "Notification area",
            note = "The app keeps an icon next to the clock. A click on it hides or shows the window; its menu also " +
                "starts and stops the dialog and quits. A green dot on it means a dialog is being listened to.",
        ) {
            addCheckBox("Minimise to the notification area", window.minimizeToTray) { on -> save { it.copy(minimizeToTray = on) } }
            addCheckBox("Close to the notification area (Exit in the icon's menu quits)", window.closeToTray) { on ->
                save { it.copy(closeToTray = on) }
            }
        }

        addSection(
            title = "Taskbar",
            note = "Without a taskbar button and an Alt+Tab entry the window is reached through the icon next to the " +
                "clock and the hotkey. Minimising then hides it.",
        ) {
            addCheckBox("Hide from the taskbar and Alt+Tab", window.hideFromTaskbar) { on -> save { it.copy(hideFromTaskbar = on) } }
        }

        addSection(
            title = "Hotkeys",
            note = "Work from any program. Click a field and press the combination: Ctrl, Alt, Shift or Win with a " +
                "letter, a digit, F1–F24 or Space; Backspace leaves the action without one. The window's hotkey " +
                "brings a window that is behind others to the front, and hides it when it is in front.",
        ) {
            val fields = HotkeyAction.entries.associateWith { action ->
                hotkeyField(window.hotkeys[action].orEmpty())
            }
            val problems = HotkeyAction.entries.associateWith { noteLabel("") }
            HotkeyAction.entries.forEach { action ->
                children.add(
                    HBox(
                        12.0,
                        Label(action.title).apply { minWidth = HOTKEY_TITLE_WIDTH; prefWidth = HOTKEY_TITLE_WIDTH },
                        fields.getValue(action),
                        problems.getValue(action),
                    ).apply { alignment = Pos.CENTER_LEFT },
                )
            }
            val status = statusLabel()
            addButtons(
                saveButton {
                    val error = hotkeysProblem(fields.mapValues { it.value.text })
                    if (error != null) {
                        status.showError(error)
                    } else {
                        val keys = fields.mapValues { (_, field) -> Hotkey.parse(field.text)?.text.orEmpty() }
                        keys.forEach { (action, text) -> fields.getValue(action).text = text }
                        save { it.copy(hotkeys = keys) }
                        status.showNote("Saved")
                    }
                },
                actionButton("Defaults") {
                    fields.forEach { (action, field) -> field.text = action.defaultKeys }
                    save { it.copy(hotkeys = HotkeyAction.entries.associateWith { action -> action.defaultKeys }) }
                    status.showNote("Saved")
                },
                status,
            )
            mainThreadScope.launch {
                windowPresence.hotkeyProblems.collect { taken ->
                    problems.forEach { (action, label) -> label.text = taken[action].orEmpty() }
                }
            }
        }

        addSection(
            title = "Floating panel",
            note = "A small window above all others with the newest question and its answer. Drag it anywhere; a " +
                "click on it opens the main window.",
        ) {
            addComboBox(
                items = FloatingPanelMode.entries,
                selectedItem = window.floatingPanel,
                toStringFn = { it.title },
                onSelected = { mode -> save { it.copy(floatingPanel = mode) } },
            )
            addComboBox(
                items = WindowSettings.PANEL_OPACITY_PERCENTS,
                selectedItem = window.panelOpacityPercent,
                toStringFn = { "$it% opaque" },
                onSelected = { percent -> save { it.copy(panelOpacityPercent = percent) } },
                short = true,
            )
        }

        addSection(
            title = "New answer signal",
            note = "An answer that comes while the window is hidden puts an orange dot on the icon next to the clock, " +
                "until the window is shown.",
        ) {
            addCheckBox("Mark the icon when an answer comes", window.newAnswerSignal) { on -> save { it.copy(newAnswerSignal = on) } }
        }
    })

    /** A field that takes the combination pressed in it, rather than typed text; Backspace or Delete empties it. */
    private fun hotkeyField(value: String) = settingsTextField(value, short = true).apply {
        isEditable = false
        promptText = "None"
        setOnKeyPressed { event ->
            if (event.code == KeyCode.TAB) return@setOnKeyPressed
            event.consume()
            if (event.code == KeyCode.BACK_SPACE || event.code == KeyCode.DELETE) {
                text = ""
                return@setOnKeyPressed
            }
            val key = when {
                event.code.isLetterKey || event.code.isFunctionKey -> event.code.getName()
                event.code.name.startsWith("DIGIT") -> event.code.getName()
                event.code == KeyCode.SPACE -> "Space"
                else -> null
            }
            val modifiers = listOfNotNull(
                "Ctrl".takeIf { event.isControlDown },
                "Alt".takeIf { event.isAltDown },
                "Shift".takeIf { event.isShiftDown },
                "Win".takeIf { event.isMetaDown },
            )
            text = (modifiers + listOfNotNull(key)).joinToString("+")
        }
    }

    /**
     * Temporary: the chain selector centres its rows itself and pads them [CHAIN_ROW_PADDING] on each side, so here
     * they are moved to the left, that padding is cancelled and the width held to [CHAIN_MAX_WIDTH], to line up with
     * the rest of the page. Remove this when the selector aligns left on its own.
     */
    private fun leftAligned(chain: VBox): VBox {
        chain.maxWidth = CHAIN_MAX_WIDTH
        chain.children.filterIsInstance<HBox>().forEach { it.alignment = Pos.CENTER_LEFT }
        VBox.setMargin(chain, Insets(0.0, 0.0, 0.0, -CHAIN_ROW_PADDING))
        return chain
    }

    private fun buildPhrasesPane(): ScrollPane = scrollOf(settingsPage {
        addSection(
            title = "Cutting speech into phrases",
            note = "Where one phrase ends and the next begins, before it is recognised and translated. The values " +
                "below serve Whisper (batch and streaming) and the voice models, and in part Speechmatics (see each " +
                "one); Sherpa-ONNX finds the pauses itself (see its page).",
        )
        children.add(
            numberSetting(
                title = "Silence threshold, %",
                description = "Sound quieter than this, as the Mic meter at the top shows it, counts as silence. " +
                    "Raise it when background noise starts phrases, lower it when quiet speech is missed.",
                value = { whisperConfig().silenceThresholdPercents },
                min = 0,
            ) { v -> saveWhisperConfig { it.copy(silenceThresholdPercents = v) } }
        )
        children.add(
            millisecondsSetting(
                title = "Pause that ends a phrase, ms",
                description = "How long the speaker must be silent before the phrase counts as finished and goes on. " +
                    "Shorter means quicker answers, but a sentence may be cut at a breath; longer keeps sentences whole " +
                    "and answers later. Speechmatics ends a phrase by itself after half a second of silence, so a " +
                    "longer pause changes nothing there.",
                value = { whisperConfig().maxSilenceMilliseconds },
                min = 1,
            ) { v -> saveWhisperConfig { it.copy(maxSilenceMilliseconds = v) } }
        )
        children.add(
            millisecondsSetting(
                title = "Longest phrase, ms",
                description = "A phrase that runs this long without a pause is cut anyway, and the speech goes on in " +
                    "the next one. Speechmatics does not use it.",
                value = { whisperConfig().maxChunkDurationMilliseconds },
                min = 1,
            ) { v -> saveWhisperConfig { it.copy(maxChunkDurationMilliseconds = v) } }
        )
    })

    private fun buildApiKeysPane(): ScrollPane = scrollOf(settingsPage {
        val appInfo = getAppInfo()
        addApiKey(
            title = "Google Cloud API key",
            note = "For the Gemini and Gemma models, the voice one included.",
            value = appInfo.googleCloudToken,
            getKeyUrl = "https://aistudio.google.com/apikey",
        ) { saveAppInfo(getAppInfo().copy(googleCloudToken = it)) }
        addApiKey(
            title = "OpenAI API key",
            note = "For the GPT models, GPT-Audio included.",
            value = appInfo.openAiToken,
            getKeyUrl = "https://platform.openai.com/api-keys",
        ) { saveAppInfo(getAppInfo().copy(openAiToken = it)) }
        addApiKey(
            title = "Claude (Anthropic) API key",
            note = "For the Claude models.",
            value = appInfo.claudeToken,
            getKeyUrl = "https://console.anthropic.com/settings/keys",
        ) { saveAppInfo(getAppInfo().copy(claudeToken = it)) }
        addApiKey(
            title = "Speechmatics API key",
            note = "For the Speechmatics speech recogniser; its other settings are on the Speechmatics page.",
            value = appInfo.speechmaticsToken,
            getKeyUrl = "https://portal.speechmatics.com/manage-access/",
        ) { saveAppInfo(getAppInfo().copy(speechmaticsToken = it)) }
    })

    private fun VBox.addApiKey(title: String, note: String, value: String, getKeyUrl: String, save: (String) -> Unit) {
        addSection(title, note) {
            val field = addTextField(value)
            val status = statusLabel(field)
            addButtons(
                saveButton {
                    save(field.text)
                    status.showNote("Saved")
                },
                pasteButton(field),
                copyButton(field),
                actionButton("Get key") { openUrl(getKeyUrl) },
                status,
            )
        }
    }

    /** A model folder: Browse fills the field, Save stores what it holds, and Download opens where the models are. */
    private fun VBox.addModelFolder(title: String, note: String, path: String, downloadUrl: String, save: (String) -> Unit) {
        addSection(title, note) {
            val field = addTextField(path)
            val status = statusLabel(field)
            addButtons(
                saveButton {
                    save(field.text.trim())
                    status.showNote("Saved")
                },
                actionButton("Browse…") {
                    ownerStage?.chooseDirectory(title = "Select folder")?.let { field.text = it.absolutePath }
                },
                actionButton("Download models") { openUrl(downloadUrl) },
                status,
            )
        }
    }

    private fun buildWhisperPane(): ScrollPane = scrollOf(settingsPage {
        addModelFolder(
            title = "Whisper model folder",
            note = "The folder with a faster-whisper model: download one, unpack it and select the folder.",
            path = getAppInfo().whisperModelPath,
            downloadUrl = "https://huggingface.co/Systran",
        ) { saveAppInfo(getAppInfo().copy(whisperModelPath = it)) }
        addSection(
            title = "Python environment",
            note = "Whisper runs in Python. This opens a console that creates the environment in the python folder " +
                "and installs the packages from requirements.txt; run it again to update them.",
        ) {
            addButtons(actionButton("Install packages") { installPythonPackages() })
        }
        children.add(
            numberSetting(
                title = "Max concurrent processes",
                description = "Whisper (batch) starts one process per phrase. When more than this many run at once, " +
                    "the oldest is stopped.",
                value = { whisperConfig().maxConcurrentProcesses },
                min = 1,
            ) { v -> saveWhisperConfig { it.copy(maxConcurrentProcesses = v) } }
        )
        children.add(
            millisecondsSetting(
                title = "Streaming step, ms",
                description = "Whisper (streaming) reads the speech again this often, so the phrase on screen updates " +
                    "as it is said. Shorter is livelier and loads the machine more.",
                value = { whisperConfig().streamingStepMilliseconds },
                min = 100,
            ) { v -> saveWhisperConfig { it.copy(streamingStepMilliseconds = v) } }
        )
        children.add(
            numberSetting(
                title = "Streaming min characters",
                description = "Whisper (streaming) sends a sentence on only once it is this long; a shorter one is " +
                    "joined with the next.",
                value = { whisperConfig().streamingMinChars },
                min = 1,
            ) { v -> saveWhisperConfig { it.copy(streamingMinChars = v) } }
        )
    })

    private fun buildSherpaPane(): ScrollPane = scrollOf(settingsPage {
        addModelFolder(
            title = "Sherpa-ONNX model folder",
            note = "A streaming model: a folder with the encoder, decoder and joiner .onnx files and tokens.txt.",
            path = getAppInfo().sherpaModelPath,
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models",
        ) { saveAppInfo(getAppInfo().copy(sherpaModelPath = it)) }
        addSection(
            title = "Decoding method",
            note = "greedy_search is the fastest; modified_beam_search is more accurate and slower.",
        ) {
            addComboBox(
                items = SherpaModelConfig.DECODING_METHODS,
                selectedItem = sherpaConfig().decodingMethod
                    .takeIf { it in SherpaModelConfig.DECODING_METHODS }
                    ?: SherpaModelConfig.DECODING_METHODS.first(),
                toStringFn = { it },
                onSelected = { method -> saveSherpaConfig { it.copy(decodingMethod = method) } },
            )
        }
        children.add(
            numberSetting(
                title = "Threads",
                description = "How many CPU threads the recogniser may use.",
                value = { sherpaConfig().numThreads },
                min = 1,
            ) { v -> saveSherpaConfig { it.copy(numThreads = v) } }
        )
        children.add(
            numberSetting(
                title = "Max active paths",
                description = "How many candidate transcriptions modified_beam_search keeps at each step; " +
                    "greedy_search ignores it.",
                value = { sherpaConfig().maxActivePaths },
                min = 1,
            ) { v -> saveSherpaConfig { it.copy(maxActivePaths = v) } }
        )
        children.add(
            numberSetting(
                title = "Max words per phrase",
                description = "A phrase this many words long is sent on even without a pause. 0 sends only at a pause.",
                value = { sherpaConfig().splitMaxWords },
                min = 0,
            ) { v -> saveSherpaConfig { it.copy(splitMaxWords = v) } }
        )
        children.add(
            millisecondsSetting(
                title = "Longest hang, ms",
                description = "A phrase that has been going this long is sent on anyway, pause or not. 0 turns it off.",
                value = { sherpaConfig().maxHangMillis },
                min = 0,
            ) { v -> saveSherpaConfig { it.copy(maxHangMillis = v) } }
        )
    })

    private fun buildSpeechmaticsPane(): ScrollPane = scrollOf(settingsPage {
        val config = getAppInfo().speechmaticsConfig
        addSection(
            title = "Endpoint",
            note = "Where the audio is sent and processed: the region in the address keeps it there. " +
                "The API key is on the API keys page.",
        ) {
            addComboBox(
                items = SpeechmaticsConfig.ENDPOINTS,
                selectedItem = config.endpoint.takeIf { it in SpeechmaticsConfig.ENDPOINTS }
                    ?: SpeechmaticsConfig.ENDPOINTS.first(),
                toStringFn = { it },
                onSelected = { endpoint -> saveSpeechmaticsConfig { it.copy(endpoint = endpoint) } },
            )
        }
        addSection(
            title = "Model",
            note = "Standard, or enhanced: more accurate, slower to settle.",
        ) {
            addComboBox(
                items = SpeechmaticsConfig.MODELS,
                selectedItem = config.model.takeIf { it in SpeechmaticsConfig.MODELS } ?: SpeechmaticsConfig.MODELS.first(),
                toStringFn = { it },
                onSelected = { model -> saveSpeechmaticsConfig { it.copy(model = model) } },
                short = true,
            )
        }
        addSection(
            title = "Speakers",
            note = "Each phrase is labelled with who said it, so the Questions mode knows who asked.",
        ) {
            addCheckBox(
                text = "Label who speaks (S1, S2…)",
                checked = config.speakerLabels,
                onToggle = { on -> saveSpeechmaticsConfig { it.copy(speakerLabels = on) } },
            )
        }
    })

    private fun whisperConfig() = getAppInfo().whisperModelConfig

    private fun saveWhisperConfig(change: (WhisperModelConfig) -> WhisperModelConfig) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(whisperModelConfig = change(appInfo.whisperModelConfig)))
    }

    private fun sherpaConfig() = getAppInfo().sherpaConfig

    private fun saveSherpaConfig(change: (SherpaModelConfig) -> SherpaModelConfig) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(sherpaConfig = change(appInfo.sherpaConfig)))
    }

    private fun saveSpeechmaticsConfig(change: (SpeechmaticsConfig) -> SpeechmaticsConfig) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(speechmaticsConfig = change(appInfo.speechmaticsConfig)))
    }

    private fun openUrl(url: String) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            log.warn("Desktop is not supported")
        }
    }

    private fun installPythonPackages() {
        val pythonDir = java.io.File(java.io.File("").absolutePath, "python")
        val command = "cd /d \"${pythonDir.absolutePath}\" && " +
            "py -m venv .venv && " +
            ".venv\\Scripts\\python.exe -m pip install --upgrade pip && " +
            ".venv\\Scripts\\python.exe -m pip install -r requirements.txt"
        try {
            ProcessBuilder("cmd", "/c", "start", "Install Python packages", "cmd", "/k", command)
                .directory(pythonDir)
                .start()
        } catch (e: Exception) {
            log.error("Failed to launch package install terminal: ${e.message}", e)
            ownerStage?.showAlert(alertTitle = "Error", alertContent = "Could not open terminal: ${e.message}")
        }
    }

}
