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
import models.domain.ClaudeModelsEnum
import models.domain.GoogleAiModelsEnum
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.OpenAiModelsEnum
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.AudioChunker
import java.awt.Desktop
import java.net.URI
import javax.sound.sampled.Mixer

class SettingsTab(
    private val audioChunker: AudioChunker,
    preferencesRepository: PreferencesRepository,
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
    }

    private val log = LoggerFactory.getLogger("SettingsTab")

    var modelsBox: ComboBox<LlmModel>? = null
    var soundDevicesBox: ComboBox<Mixer.Info>? = null
    var whisperModelPathTextInput: TextInputControl? = null
    var googleCloudTokenTextInput: TextInputControl? = null
    var openAiTokenTextInput: TextInputControl? = null
    var claudeTokenTextInput: TextInputControl? = null
    var silenceThresholdPercentsTextInput: TextInputControl? = null
    var maxSilenceMillisecondsTextInput: TextInputControl? = null
    var maxChunkDurationMillisecondsTextInput: TextInputControl? = null
    var maxConcurrentWhisperProcessesTextInput: TextInputControl? = null

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val appInfo = getAppInfo()

        addTitleLabel("Audio device for voice recognition:")

        val devices: List<Mixer.Info> = audioChunker.getAvailableInputDevices()

        if (devices.isEmpty()) {
            addLabel("Not found")
        } else {
            val lastSelected = devices.firstOrNull {
                appInfo.lastSelectedDevice == it.name
            }
            if (lastSelected == null) {
                saveAppInfo(
                    getAppInfo().copy(
                        lastSelectedDevice = devices.first().name
                    )
                )
            }
            soundDevicesBox = addComboBox(
                items = devices,
                selectedItem = lastSelected ?: devices.first(),
                toStringFn = { mixerInfo -> mixerInfo.name },
                onSelected = { mixerInfo ->
                    saveAppInfo(
                        getAppInfo().copy(
                            lastSelectedDevice = mixerInfo.name
                        )
                    )
                }
            )
        }

        addTitleLabel("AI model:")

        modelsBox = addComboBox(
            items = getModelsList(),
            selectedItem = appInfo.selectedModel,
            toStringFn = { it.description ?: "" },
            onSelected = {
                saveAppInfo(
                    getAppInfo().copy(
                        selectedModel = it
                    )
                )
            }
        )

        addTitleLabel("Question language")

        addTextFieldWithSaveButton(
            fieldText = appInfo.questionLanguage,
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { result ->
                saveAppInfo(
                    getAppInfo().copy(questionLanguage = result)
                )
            }
        )

        addTitleLabel("Silence threshold percents for Whisper")

        silenceThresholdPercentsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.silenceThresholdPercents.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { raw ->
                val result = raw.toIntOrNull()
                if (result == null) {
                    ownerStage?.showAlert(alertTitle = "Error", alertContent = "Can not get number")
                    return@addTextFieldWithSaveButton
                }
                val current = getAppInfo()
                saveAppInfo(
                    current.copy(
                        whisperModelConfig = current.whisperModelConfig.copy(
                            silenceThresholdPercents = result
                        )
                    )
                )
            }
        )

        addTitleLabel("Max silence milliseconds for Whisper")

        maxSilenceMillisecondsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.maxSilenceMilliseconds.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { raw ->
                val result = raw.toLongOrNull()
                if (result == null) {
                    ownerStage?.showAlert(alertTitle = "Error", alertContent = "Can not get number")
                    return@addTextFieldWithSaveButton
                }
                val current = getAppInfo()
                saveAppInfo(
                    current.copy(
                        whisperModelConfig = current.whisperModelConfig.copy(
                            maxSilenceMilliseconds = result
                        )
                    )
                )
            }
        )

        addTitleLabel("Max chunk duration milliseconds for Whisper")

        maxChunkDurationMillisecondsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.maxChunkDurationMilliseconds.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { raw ->
                val result = raw.toLongOrNull()
                if (result == null) {
                    ownerStage?.showAlert(alertTitle = "Error", alertContent = "Can not get number")
                    return@addTextFieldWithSaveButton
                }
                val current = getAppInfo()
                saveAppInfo(
                    current.copy(
                        whisperModelConfig = current.whisperModelConfig.copy(
                            maxChunkDurationMilliseconds = result
                        )
                    )
                )
            }
        )

        addTitleLabel("Max concurrent Whisper processes (oldest is killed when exceeded)")

        maxConcurrentWhisperProcessesTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.maxConcurrentProcesses.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = { raw ->
                val result = raw.toIntOrNull()
                if (result == null || result < 1) {
                    ownerStage?.showAlert(alertTitle = "Error", alertContent = "Must be an integer >= 1")
                    return@addTextFieldWithSaveButton
                }
                val current = getAppInfo()
                saveAppInfo(
                    current.copy(
                        whisperModelConfig = current.whisperModelConfig.copy(
                            maxConcurrentProcesses = result
                        )
                    )
                )
            }
        )

        addTitleLabel("Whisper voice recognition model download:")

        addButton(
            title = "Download Whisper model",
            buttonColor = COLOUR_BLUE,
            onClicked = {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://huggingface.co/Systran"))
                } else {
                    log.warn("Desktop is not supported")
                }
            }
        )

        addTitleLabel("Whisper voice recognition model folder:")

        whisperModelPathTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelPath,
            buttonTitle = "Select folder with model files",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                val selectedDirectory = ownerStage?.chooseDirectory(title = "Select Folder")
                if (selectedDirectory == null) {
                    log.error("Select model folder but selected directory is null")
                    return@addTextFieldWithSaveButton
                }
                saveAppInfo(
                    getAppInfo().copy(
                        whisperModelPath = selectedDirectory.absolutePath
                    )
                )
            }
        )

        addTitleLabel("Google Cloud API key:")

        googleCloudTokenTextInput = addTextFieldWithCopyPasteActionButtons(
            fieldText = appInfo.googleCloudToken,
            onClicked = {
                saveAppInfo(getAppInfo().copy(googleCloudToken = it))
            }
        )

        addButton(
            title = "Get Google API key",
            buttonColor = COLOUR_BLUE,
            onClicked = {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://aistudio.google.com/apikey"))
                } else {
                    log.warn("Desktop is not supported")
                }
            }
        )

        addTitleLabel("OpenAI API key:")

        openAiTokenTextInput = addTextFieldWithCopyPasteActionButtons(
            fieldText = appInfo.openAiToken,
            onClicked = {
                saveAppInfo(getAppInfo().copy(openAiToken = it))
            }
        )

        addButton(
            title = "Get OpenAI API key",
            buttonColor = COLOUR_BLUE,
            onClicked = {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://platform.openai.com/api-keys"))
                } else {
                    log.warn("Desktop is not supported")
                }
            }
        )

        addTitleLabel("Claude (Anthropic) API key:")

        claudeTokenTextInput = addTextFieldWithCopyPasteActionButtons(
            fieldText = appInfo.claudeToken,
            onClicked = {
                saveAppInfo(getAppInfo().copy(claudeToken = it))
            }
        )

        addButton(
            title = "Get Claude API key",
            buttonColor = COLOUR_BLUE,
            onClicked = {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://console.anthropic.com/settings/keys"))
                } else {
                    log.warn("Desktop is not supported")
                }
            }
        )

        mainThreadScope.launch {
            preferencesRepository.appInfoFlow.collect { appInfo ->
                googleCloudTokenTextInput?.text = appInfo.googleCloudToken
                openAiTokenTextInput?.text = appInfo.openAiToken
                claudeTokenTextInput?.text = appInfo.claudeToken
                whisperModelPathTextInput?.text = appInfo.whisperModelPath
                silenceThresholdPercentsTextInput?.text = appInfo.whisperModelConfig.silenceThresholdPercents.toString()
                maxSilenceMillisecondsTextInput?.text = appInfo.whisperModelConfig.maxSilenceMilliseconds.toString()
                maxChunkDurationMillisecondsTextInput?.text = appInfo.whisperModelConfig.maxChunkDurationMilliseconds.toString()
                maxConcurrentWhisperProcessesTextInput?.text = appInfo.whisperModelConfig.maxConcurrentProcesses.toString()
            }
        }
    }

    private fun getModelsList(): List<LlmModel> {
        return buildList {
            GoogleAiModelsEnum.entries.forEach {
                add(
                    LlmModel(
                        id = it.type,
                        description = it.getDescription(),
                        engine = LlmModelEngine.GOOGLE,
                        googleAiModel = it,
                    )
                )
            }
            OpenAiModelsEnum.entries.forEach {
                add(
                    LlmModel(
                        id = it.type,
                        description = it.getDescription(),
                        engine = LlmModelEngine.OPENAI,
                        openAiModel = it,
                    )
                )
            }
            ClaudeModelsEnum.entries.forEach {
                add(
                    LlmModel(
                        id = it.type,
                        description = it.getDescription(),
                        engine = LlmModelEngine.CLAUDE,
                        claudeModel = it,
                    )
                )
            }
        }
    }

}
