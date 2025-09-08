package ui

import COLOUR_BLUE
import COLOUR_GREEN
import app.DialogApplication.Companion.ownerStage
import backgroundThreadScope
import extensions.*
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.ComboBox
import javafx.scene.control.TextInputControl
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mainThreadScope
import models.domain.GoogleAiModelsEnum
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.VoiceRecognizer
import org.slf4j.LoggerFactory
import repository.LocalNetworkRepository
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.VoskVoiceRecognizer
import java.awt.Desktop
import java.net.URI
import javax.sound.sampled.Mixer

class SettingsTab(
    private val voskVoiceRecognizer: VoskVoiceRecognizer,
    private val localNetworkRepository: LocalNetworkRepository,
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
    }

    private val log = LoggerFactory.getLogger("SettingsTab")

    var modelsBox: ComboBox<LlmModel>? = null
    var voskModelPathTextInput: TextInputControl? = null
    var whisperModelPathTextInput: TextInputControl? = null
    var googleCloudTokenTextInput: TextInputControl? = null
    var lmStudioBaseUrlTextInput: TextInputControl? = null
    var ollamaBaseUrlTextInput: TextInputControl? = null
    var sendTextEverySymbolsTextInput: TextInputControl? = null
    var sendTextEveryMillisecondsTextInput: TextInputControl? = null
    var silenceThresholdPercentsTextInput: TextInputControl? = null
    var maxSilenceMillisecondsTextInput: TextInputControl? = null
    var maxChunkDurationMillisecondsTextInput: TextInputControl? = null

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val appInfo = getAppInfo()

        addTitleLabel("Audio device for voice recognition:")

        val devices: List<Mixer.Info> = voskVoiceRecognizer.getAvailableInputDevices()

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
            addComboBox(
                items = devices,
                selectedItem = lastSelected ?: devices.first(),
                toStringFn = { mixerInfo ->
                    mixerInfo.name
                },
                onSelected = { mixerInfo ->
                    saveAppInfo(
                        getAppInfo().copy(
                            lastSelectedDevice = mixerInfo.name
                        )
                    )
                }
            )
        }

        addTitleLabel("Voice recognizer:")

        addComboBox(
            items = VoiceRecognizer.entries,
            selectedItem = appInfo.voiceRecognizer,
            toStringFn = { voiceRecognizer ->
                voiceRecognizer.type
            },
            onSelected = { voiceRecognizer ->
                saveAppInfo(
                    getAppInfo().copy(
                        voiceRecognizer = voiceRecognizer
                    )
                )
                if (voiceRecognizer == VoiceRecognizer.VOSK) {
                    val appInfo = getAppInfo()
                    if (appInfo.voskModelPath.isNotEmpty()) {
                        voskVoiceRecognizer.init(appInfo.voskModelPath)
                    }
                }
            }
        )

        addTitleLabel("AI model:")

        modelsBox = addComboBox(
            items = getModelsList(),
            selectedItem = appInfo.selectedModel,
            toStringFn = {
                it.description ?: ""
            },
            onSelected = {
                saveAppInfo(
                    getAppInfo().copy(
                        selectedModel = it
                    )
                )
            }
        )

        addTitleLabel("Send to AI model every N characters, 0 - do not use:")

        sendTextEverySymbolsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.sendTextEverySymbols.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                try {
                    val result = it.toIntOrNull()
                    if (result == null) {
                        ownerStage?.showAlert(
                            alertTitle = "Error",
                            alertContent = "Can not get number"
                        )
                        return@addTextFieldWithSaveButton
                    }
                    val appInfo = getAppInfo()
                    saveAppInfo(
                        appInfo.copy(
                            sendTextEverySymbols = result,
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    log.error(e.message)
                    ownerStage?.showAlert(
                        alertTitle = "Error",
                        alertContent = e.message ?: "Can not get number"
                    )
                }
            }
        )

        addTitleLabel("Send to AI model every N milliseconds, 0 - do not use:")

        sendTextEveryMillisecondsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.sendTextEveryMilliseconds.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                try {
                    val result = it.toLongOrNull()
                    if (result == null) {
                        ownerStage?.showAlert(
                            alertTitle = "Error",
                            alertContent = "Can not get number"
                        )
                        return@addTextFieldWithSaveButton
                    }
                    val appInfo = getAppInfo()
                    saveAppInfo(
                        appInfo.copy(
                            sendTextEveryMilliseconds = result,
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    log.error(e.message)
                    ownerStage?.showAlert(
                        alertTitle = "Error",
                        alertContent = e.message ?: "Can not get number"
                    )
                }
            }
        )

        addTitleLabel("Silence threshold percents for Whisper")

        silenceThresholdPercentsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.silenceThresholdPercents.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                try {
                    val result = it.toIntOrNull()
                    if (result == null) {
                        ownerStage?.showAlert(
                            alertTitle = "Error",
                            alertContent = "Can not get number"
                        )
                        return@addTextFieldWithSaveButton
                    }
                    val appInfo = getAppInfo()
                    saveAppInfo(
                        appInfo.copy(
                            whisperModelConfig = appInfo.whisperModelConfig.copy(
                                silenceThresholdPercents = result
                            )
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    log.error(e.message)
                    ownerStage?.showAlert(
                        alertTitle = "Error",
                        alertContent = e.message ?: "Can not get number"
                    )
                }
            }
        )

        addTitleLabel("Max silence milliseconds for Whisper")

        maxSilenceMillisecondsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.maxSilenceMilliseconds.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                try {
                    val result = it.toLongOrNull()
                    if (result == null) {
                        ownerStage?.showAlert(
                            alertTitle = "Error",
                            alertContent = "Can not get number"
                        )
                        return@addTextFieldWithSaveButton
                    }
                    val appInfo = getAppInfo()
                    saveAppInfo(
                        appInfo.copy(
                            whisperModelConfig = appInfo.whisperModelConfig.copy(
                                maxSilenceMilliseconds = result
                            )
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    log.error(e.message)
                    ownerStage?.showAlert(
                        alertTitle = "Error",
                        alertContent = e.message ?: "Can not get number"
                    )
                }
            }
        )

        addTitleLabel("Max chunk duration milliseconds for Whisper")

        maxChunkDurationMillisecondsTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelConfig.maxChunkDurationMilliseconds.toString(),
            buttonTitle = "Save",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                try {
                    val result = it.toLongOrNull()
                    if (result == null) {
                        ownerStage?.showAlert(
                            alertTitle = "Error",
                            alertContent = "Can not get number"
                        )
                        return@addTextFieldWithSaveButton
                    }
                    val appInfo = getAppInfo()
                    saveAppInfo(
                        appInfo.copy(
                            whisperModelConfig = appInfo.whisperModelConfig.copy(
                                maxChunkDurationMilliseconds = result
                            )
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    log.error(e.message)
                    ownerStage?.showAlert(
                        alertTitle = "Error",
                        alertContent = e.message ?: "Can not get number"
                    )
                }
            }
        )

        addTitleLabel("VOSK voice recognition model:")

        addLabel("To use voice recognition, you must download the model for recognition, unzip it to disk and select the folder with it.")

        addButton(
            title = "Download model",
            buttonColor = COLOUR_BLUE,
            onClicked = {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://alphacephei.com/vosk/models"))
                } else {
                    println("Desktop is not supported")
                }
            }
        )

        addTitleLabel("VOSK voice recognition model folder:")

        voskModelPathTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.voskModelPath,
            buttonTitle = "Select folder with model files",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                voskVoiceRecognizer.selectModelFolder()
            }
        )

        addTitleLabel("Whisper voice recognition model folder:")

        whisperModelPathTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.whisperModelPath,
            buttonTitle = "Select folder with model files",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                val selectedDirectory = ownerStage?.chooseDirectory(
                    title = "Select Folder"
                )
                if (selectedDirectory == null) {
                    log.error("Select model folder but selected directory is null")
                    return@addTextFieldWithSaveButton
                }
                preferencesRepository.saveAppInfo(
                    preferencesRepository.getAppInfo().copy(
                        whisperModelPath = selectedDirectory.absolutePath
                    )
                )
            }
        )

        addTitleLabel("Google Cloud API key:")

        googleCloudTokenTextInput = addTextFieldWithCopyPasteActionButtons(
            fieldText = appInfo.googleCloudToken,
            onClicked = {
                saveAppInfo(
                    getAppInfo().copy(
                        googleCloudToken = it
                    )
                )
            }
        )

        addButton(
            title = "Get API key",
            buttonColor = COLOUR_BLUE,
            onClicked = {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://cloud.google.com/docs/authentication/api-keys"))
                } else {
                    println("Desktop is not supported")
                }
            }
        )

        addTitleLabel("LM Studio url")

        lmStudioBaseUrlTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.lmStudioConfig.baseUrl,
            buttonTitle = "Update LM Studio models",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                val appInfo = getAppInfo()
                saveAppInfo(
                    appInfo.copy(
                        lmStudioConfig = appInfo.lmStudioConfig.copy(
                            baseUrl = it
                        ),
                    )
                )
                getLmStudioModels(
                    baseUrl = appInfo.lmStudioConfig.baseUrl,
                )
            }
        )

        addTitleLabel("Ollama url")

        ollamaBaseUrlTextInput = addTextFieldWithSaveButton(
            fieldText = appInfo.ollamaConfig.baseUrl,
            buttonTitle = "Update Ollama models",
            buttonColor = COLOUR_GREEN,
            onClicked = {
                val appInfo = getAppInfo()
                saveAppInfo(
                    appInfo.copy(
                        ollamaConfig = appInfo.ollamaConfig.copy(
                            baseUrl = it
                        ),
                    )
                )
                getOllamaModels(
                    baseUrl = appInfo.ollamaConfig.baseUrl,
                )
            }
        )

        mainThreadScope.launch {
            preferencesRepository.appInfoFlow.collect { appInfo ->
                voskModelPathTextInput?.text = appInfo.voskModelPath
                googleCloudTokenTextInput?.text = appInfo.googleCloudToken
                lmStudioBaseUrlTextInput?.text = appInfo.lmStudioConfig.baseUrl
                ollamaBaseUrlTextInput?.text = appInfo.ollamaConfig.baseUrl
                sendTextEverySymbolsTextInput?.text = appInfo.sendTextEverySymbols.toString()
                sendTextEveryMillisecondsTextInput?.text = appInfo.sendTextEveryMilliseconds.toString()
                whisperModelPathTextInput?.text = appInfo.whisperModelPath
                silenceThresholdPercentsTextInput?.text = appInfo.whisperModelConfig.silenceThresholdPercents.toString()
                maxSilenceMillisecondsTextInput?.text = appInfo.whisperModelConfig.maxSilenceMilliseconds.toString()
                maxChunkDurationMillisecondsTextInput?.text =
                    appInfo.whisperModelConfig.maxChunkDurationMilliseconds.toString()
            }
        }

    }

    private fun getModelsList(): List<LlmModel> {
        val appInfo = getAppInfo()
        return buildList {
            appInfo.lmStudioConfig.models.forEach {
                add(
                    LlmModel(
                        id = it.id,
                        description = "LM Studio Local: ${it.id}",
                        engine = LlmModelEngine.LM_STUDIO,
                        googleAiModel = null,
                    )
                )
            }
            appInfo.ollamaConfig.models.forEach {
                add(
                    LlmModel(
                        id = it.id,
                        description = "Ollama Local: ${it.id}",
                        engine = LlmModelEngine.OLLAMA,
                        googleAiModel = null,
                    )
                )
            }
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
        }
    }

    private fun getLmStudioModels(baseUrl: String) {
        backgroundThreadScope.launch {
            val result = localNetworkRepository.getLmStudioModels(
                baseUrl = baseUrl,
            )
            if (!result.isSuccess) {
                log.error("getLmStudioModels result error: ${result.exceptionOrNull()}")
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = result.exceptionOrNull()?.message ?: ""
                )
                return@launch
            }
            val resultData = result.getOrNull()
            if (resultData.isNullOrEmpty()) {
                log.error("getLmStudioModels resultData == null")
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = "Empty data received"
                )
                return@launch

            }
            val appInfo = getAppInfo()
            saveAppInfo(
                appInfo.copy(
                    lmStudioConfig = appInfo.lmStudioConfig.copy(
                        models = resultData
                    ),
                )
            )
            withContext(Dispatchers.JavaFx) {
                modelsBox?.items = FXCollections.observableArrayList(getModelsList())
                val selected = getAppInfo().selectedModel
                resultData.firstOrNull {
                    it.id == selected.id
                }?.let {
                    modelsBox?.selectionModel?.select(it)
                }
            }
        }
    }

    private fun getOllamaModels(baseUrl: String) {
        backgroundThreadScope.launch {
            val result = localNetworkRepository.getOllamaModels(
                baseUrl = baseUrl,
            )
            if (!result.isSuccess) {
                log.error("getOllamaModels result error: ${result.exceptionOrNull()}")
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = result.exceptionOrNull()?.message ?: ""
                )
                return@launch
            }
            val resultData = result.getOrNull()
            if (resultData.isNullOrEmpty()) {
                log.error("getOllamaModels resultData == null")
                ownerStage?.showAlert(
                    alertTitle = "Error",
                    alertContent = "Empty data received"
                )
                return@launch

            }
            val appInfo = getAppInfo()
            saveAppInfo(
                appInfo.copy(
                    ollamaConfig = appInfo.ollamaConfig.copy(
                        models = resultData
                    ),
                )
            )
            withContext(Dispatchers.JavaFx) {
                modelsBox?.items = FXCollections.observableArrayList(getModelsList())
                val selected = getAppInfo().selectedModel
                resultData.firstOrNull {
                    it.id == selected.id
                }?.let {
                    modelsBox?.selectionModel?.select(it)
                }
            }
        }
    }

}
