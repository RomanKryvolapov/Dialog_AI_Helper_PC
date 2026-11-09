package ui

import COLOUR_BLUE
import COLOUR_GREEN
import app.DialogApplication.Companion.ownerStage
import extensions.addButton
import extensions.addComboBox
import extensions.addLabel
import extensions.addTextFieldWithCopyPasteActionButtons
import extensions.addTextFieldWithSaveButton
import extensions.addTitleLabel
import extensions.chooseDirectory
import extensions.showAlert
import javafx.geometry.Insets
import javafx.scene.control.ComboBox
import javafx.scene.control.TextInputControl
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.SherpaModelConfig
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.AudioChunker
import java.awt.Desktop
import java.net.URI
import javax.sound.sampled.Mixer


import extensions.addCheckBox
import javafx.scene.control.CheckBox

import javafx.scene.control.ScrollPane
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import models.domain.ApplicationInfo

class SettingsTab(
    private val audioChunker: AudioChunker,
    private val promptTab: PromptTab,
    private val consoleTab: ConsoleTab,
    private val methodChainSelector: MethodChainSelector,
    preferencesRepository: PreferencesRepository,
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
        private const val SIDEBAR_BACKGROUND_COLOUR = "#2b2b2b"
    }

    private val log = LoggerFactory.getLogger("SettingsTab")

    private val detailContainer = StackPane().apply {
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))
    }

    val content: BorderPane = BorderPane().apply {
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val appInfo = getAppInfo()

        val generalScroll = buildGeneralPane(appInfo)
        val llmScroll = buildLlmPane(appInfo)
        val whisperBatchScroll = buildWhisperBatchPane(appInfo)
        val whisperStreamingScroll = buildWhisperStreamingPane(appInfo)
        val sherpaScroll = buildSherpaPane(appInfo)
        val promptsScroll = wrapNode(promptTab.content)
        val consoleScroll = wrapNode(consoleTab.content)

        val group = ToggleGroup()
        val generalCategoryButton = categoryButton("General", group) { showCategory(generalScroll) }
        val llmCategoryButton = categoryButton("LLM", group) { showCategory(llmScroll) }
        val whisperBatchCategoryButton = categoryButton("Whisper (batch)", group) { showCategory(whisperBatchScroll) }
        val whisperStreamingCategoryButton = categoryButton("Whisper (streaming)", group) { showCategory(whisperStreamingScroll) }
        val sherpaCategoryButton = categoryButton("Sherpa-ONNX", group) { showCategory(sherpaScroll) }
        val promptsCategoryButton = categoryButton("Prompts", group) { showCategory(promptsScroll) }
        val consoleCategoryButton = categoryButton("Console", group) { showCategory(consoleScroll) }

        val sidebar = VBox(
            8.0,
            generalCategoryButton,
            llmCategoryButton,
            whisperBatchCategoryButton,
            whisperStreamingCategoryButton,
            sherpaCategoryButton,
            promptsCategoryButton,
            consoleCategoryButton,
        ).apply {
            padding = Insets(20.0, 12.0, 20.0, 12.0)
            prefWidth = 240.0
            minWidth = 240.0
            background = Background(BackgroundFill(Color.web(SIDEBAR_BACKGROUND_COLOUR), null, null))
        }

        left = sidebar
        center = detailContainer

        generalCategoryButton.isSelected = true
        showCategory(generalScroll)
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

    private fun showCategory(node: javafx.scene.Node) {
        detailContainer.children.setAll(node)
    }

    private fun scrollOf(box: VBox): ScrollPane {
        return ScrollPane(box).apply {
            isFitToWidth = true
            isPannable = true
            style = "-fx-background: $WINDOW_BACKGROUND_COLOUR; -fx-background-color: $WINDOW_BACKGROUND_COLOUR;"
        }
    }

    private fun wrapNode(node: javafx.scene.Node): ScrollPane {
        return ScrollPane(node).apply {
            isFitToWidth = true
            isFitToHeight = true
            isPannable = true
            style = "-fx-background: $WINDOW_BACKGROUND_COLOUR; -fx-background-color: $WINDOW_BACKGROUND_COLOUR;"
        }
    }

    private fun buildGeneralPane(appInfo: ApplicationInfo): ScrollPane {
        val box = VBox(4.0).apply {
            padding = Insets(0.0, 20.0, 20.0, 20.0)

            addTitleLabel("Translation method: 1 first, then 2 and 3 when the one before fails")
            addLabel("How the speech is heard → which model translates (only a speech recogniser needs one)")
            children.add(methodChainSelector.node)

            addTitleLabel("Audio device for voice recognition:")
            val devices: List<Mixer.Info> = audioChunker.getAvailableInputDevices()
            if (devices.isEmpty()) {
                addLabel("Not found")
            } else {
                // Not saved when it falls back to the first device: that would turn a stand-in into
                // a stored choice and stop `.env` (or a device plugged in later) from ever applying.
                val lastSelected = devices.firstOrNull { appInfo.lastSelectedDevice == it.name }
                addComboBox(
                    items = devices,
                    selectedItem = lastSelected ?: devices.first(),
                    toStringFn = { mixerInfo -> mixerInfo.name },
                    onSelected = { mixerInfo -> saveAppInfo(getAppInfo().copy(lastSelectedDevice = mixerInfo.name)) }
                )
            }

            addTitleLabel("Question language")
            addTextFieldWithSaveButton(
                fieldText = appInfo.questionLanguage,
                buttonTitle = "Save",
                buttonColor = COLOUR_GREEN,
                onClicked = { result -> saveAppInfo(getAppInfo().copy(questionLanguage = result)) }
            )
        }
        return scrollOf(box)
    }

    private fun buildLlmPane(appInfo: ApplicationInfo): ScrollPane {
        val box = VBox(4.0).apply {
            padding = Insets(0.0, 20.0, 20.0, 20.0)

            addCheckBox(
                text = "Use streaming response (show translation as it is generated)",
                checked = appInfo.useStreamingResponse,
                onToggle = { checked -> saveAppInfo(getAppInfo().copy(useStreamingResponse = checked)) },
            )

            addTitleLabel("Google Cloud API key:")
            addTextFieldWithCopyPasteActionButtons(
                fieldText = appInfo.googleCloudToken,
                onClicked = { saveAppInfo(getAppInfo().copy(googleCloudToken = it)) }
            )
            addButton(title = "Get Google API key", buttonColor = COLOUR_BLUE, onClicked = { openUrl("https://aistudio.google.com/apikey") })

            addTitleLabel("OpenAI API key:")
            addTextFieldWithCopyPasteActionButtons(
                fieldText = appInfo.openAiToken,
                onClicked = { saveAppInfo(getAppInfo().copy(openAiToken = it)) }
            )
            addButton(title = "Get OpenAI API key", buttonColor = COLOUR_BLUE, onClicked = { openUrl("https://platform.openai.com/api-keys") })

            addTitleLabel("Claude (Anthropic) API key:")
            addTextFieldWithCopyPasteActionButtons(
                fieldText = appInfo.claudeToken,
                onClicked = { saveAppInfo(getAppInfo().copy(claudeToken = it)) }
            )
            addButton(title = "Get Claude API key", buttonColor = COLOUR_BLUE, onClicked = { openUrl("https://console.anthropic.com/settings/keys") })

            addTitleLabel("Speechmatics API key:")
            addTextFieldWithCopyPasteActionButtons(
                fieldText = appInfo.speechmaticsToken,
                onClicked = { saveAppInfo(getAppInfo().copy(speechmaticsToken = it)) }
            )
            addButton(title = "Get Speechmatics API key", buttonColor = COLOUR_BLUE, onClicked = { openUrl("https://portal.speechmatics.com/manage-access/") })
        }
        return scrollOf(box)
    }

    private fun VBox.addWhisperModelControls(appInfo: ApplicationInfo) {
        addTitleLabel("Whisper model download:")
        addButton(title = "Download Whisper model", buttonColor = COLOUR_BLUE, onClicked = { openUrl("https://huggingface.co/Systran") })

        addTitleLabel("Whisper model folder:")
        var modelField: javafx.scene.control.TextInputControl? = null
        modelField = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelPath,
            buttonTitle = "Select folder with model files",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                val selectedDirectory = ownerStage?.chooseDirectory(title = "Select Folder")
                if (selectedDirectory == null) {
                    log.error("Select model folder but selected directory is null")
                    return@addTextFieldWithSaveButton
                }
                modelField?.text = selectedDirectory.absolutePath
                saveAppInfo(getAppInfo().copy(whisperModelPath = selectedDirectory.absolutePath))
            }
        )
    }

    private fun VBox.addIntField(title: String, value: Int, min: Int, save: (Int) -> Unit) {
        addTitleLabel(title)
        addTextFieldWithSaveButton(
            fieldText = value.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { raw ->
                val result = raw.toIntOrNull()
                if (result == null || result < min) {
                    ownerStage?.showAlert(alertTitle = "Error", alertContent = "Must be an integer >= $min")
                    return@addTextFieldWithSaveButton
                }
                save(result)
            }
        )
    }

    private fun VBox.addLongField(title: String, value: Long, min: Long, save: (Long) -> Unit) {
        addTitleLabel(title)
        addTextFieldWithSaveButton(
            fieldText = value.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { raw ->
                val result = raw.toLongOrNull()
                if (result == null || result < min) {
                    ownerStage?.showAlert(alertTitle = "Error", alertContent = "Must be an integer >= $min")
                    return@addTextFieldWithSaveButton
                }
                save(result)
            }
        )
    }

    private fun buildWhisperBatchPane(appInfo: ApplicationInfo): ScrollPane {
        val box = VBox(4.0).apply {
            padding = Insets(0.0, 20.0, 20.0, 20.0)
            addWhisperModelControls(appInfo)
            addTitleLabel("Python environment:")
            addButton(
                title = "Install / update Python packages (requirements.txt)",
                buttonColor = COLOUR_BLUE,
                onClicked = { installPythonPackages() }
            )
            addIntField("Silence threshold percents", appInfo.whisperModelConfig.silenceThresholdPercents, 0) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(silenceThresholdPercents = v)))
            }
            addLongField("Max silence milliseconds (end of phrase)", appInfo.whisperModelConfig.maxSilenceMilliseconds, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(maxSilenceMilliseconds = v)))
            }
            addLongField("Max chunk duration milliseconds (force cut)", appInfo.whisperModelConfig.maxChunkDurationMilliseconds, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(maxChunkDurationMilliseconds = v)))
            }
            addIntField("Max concurrent Whisper processes (oldest killed when exceeded)", appInfo.whisperModelConfig.maxConcurrentProcesses, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(maxConcurrentProcesses = v)))
            }
        }
        return scrollOf(box)
    }

    private fun buildWhisperStreamingPane(appInfo: ApplicationInfo): ScrollPane {
        val box = VBox(4.0).apply {
            padding = Insets(0.0, 20.0, 20.0, 20.0)
            addWhisperModelControls(appInfo)
            addIntField("Silence threshold percents", appInfo.whisperModelConfig.silenceThresholdPercents, 0) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(silenceThresholdPercents = v)))
            }
            addLongField("Max silence milliseconds (flush trailing phrase)", appInfo.whisperModelConfig.maxSilenceMilliseconds, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(maxSilenceMilliseconds = v)))
            }
            addLongField("Max utterance milliseconds (safety force-finalize)", appInfo.whisperModelConfig.maxChunkDurationMilliseconds, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(maxChunkDurationMilliseconds = v)))
            }
            addLongField("Streaming step, ms (how often partial results are produced)", appInfo.whisperModelConfig.streamingStepMilliseconds, 100) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(streamingStepMilliseconds = v)))
            }
            addIntField("Min characters per phrase (groups shorter sentences)", appInfo.whisperModelConfig.streamingMinChars, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(whisperModelConfig = c.whisperModelConfig.copy(streamingMinChars = v)))
            }
        }
        return scrollOf(box)
    }

    private fun buildSherpaPane(appInfo: ApplicationInfo): ScrollPane {
        val box = VBox(4.0).apply {
            padding = Insets(0.0, 20.0, 20.0, 20.0)

            addTitleLabel("Sherpa-ONNX streaming model folder (encoder/decoder/joiner .onnx + tokens.txt):")
            var sherpaField: javafx.scene.control.TextInputControl? = null
            sherpaField = addTextFieldWithSaveButton(
                fieldText = appInfo.sherpaModelPath,
                buttonTitle = "Select folder with sherpa model files",
                buttonColor = COLOUR_GREEN,
                onClicked = {
                    val selectedDirectory = ownerStage?.chooseDirectory(title = "Select Folder")
                    if (selectedDirectory == null) {
                        log.error("Select sherpa model folder but selected directory is null")
                        return@addTextFieldWithSaveButton
                    }
                    sherpaField?.text = selectedDirectory.absolutePath
                    saveAppInfo(getAppInfo().copy(sherpaModelPath = selectedDirectory.absolutePath))
                }
            )
            addButton(
                title = "Download sherpa-onnx streaming models",
                buttonColor = COLOUR_BLUE,
                onClicked = { openUrl("https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models") }
            )

            addTitleLabel("Decoding method:")
            addComboBox(
                items = SherpaModelConfig.DECODING_METHODS,
                selectedItem = appInfo.sherpaConfig.decodingMethod
                    .takeIf { it in SherpaModelConfig.DECODING_METHODS }
                    ?: SherpaModelConfig.DECODING_METHODS.first(),
                toStringFn = { it },
                onSelected = { method ->
                    val c = getAppInfo()
                    saveAppInfo(c.copy(sherpaConfig = c.sherpaConfig.copy(decodingMethod = method)))
                }
            )

            addIntField("Threads (numThreads)", appInfo.sherpaConfig.numThreads, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(sherpaConfig = c.sherpaConfig.copy(numThreads = v)))
            }
            addIntField("Max active paths (for modified_beam_search)", appInfo.sherpaConfig.maxActivePaths, 1) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(sherpaConfig = c.sherpaConfig.copy(maxActivePaths = v)))
            }
            addIntField("Max words per message (0 = split only on pause)", appInfo.sherpaConfig.splitMaxWords, 0) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(sherpaConfig = c.sherpaConfig.copy(splitMaxWords = v)))
            }
            addLongField("Force-translate after ms (max time a phrase may hang; 0 = off)", appInfo.sherpaConfig.maxHangMillis, 0) { v ->
                val c = getAppInfo(); saveAppInfo(c.copy(sherpaConfig = c.sherpaConfig.copy(maxHangMillis = v)))
            }
        }
        return scrollOf(box)
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
