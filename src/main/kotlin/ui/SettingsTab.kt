package ui

import COLOUR_GREEN
import backgroundThreadScope
import extensions.*
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.ListCell
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.domain.ApplicationLanguage
import models.domain.GoogleAiModelsEnum
import models.domain.LlmModel
import models.domain.LlmModelEngine
import org.slf4j.LoggerFactory
import repository.LocalNetworkRepository
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.VoskRecognizer
import javax.sound.sampled.Mixer

class SettingsTab(
    private val voskRecognizer: VoskRecognizer,
    private val localNetworkRepository: LocalNetworkRepository,
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
    }

    private val log = LoggerFactory.getLogger("SettingsTabTag")

    val currentToggleGroup = ToggleGroup()


    var modelsBox: ComboBox<LlmModel>? = null

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val appInfo = getAppInfo()

        addTitleLabel("Audio device for voice recognition:")

        val devices: List<Mixer.Info> = voskRecognizer.getAvailableInputDevices()

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

        addTitleLabel("Translate From / To Language:")
        val translateFromComboBox = ComboBox(
            FXCollections.observableArrayList(ApplicationLanguage.entries)
        ).apply {
            cellFactory = javafx.util.Callback {
                object : ListCell<ApplicationLanguage>() {
                    override fun updateItem(item: ApplicationLanguage?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (item == null || empty) null else item.name
                    }
                }
            }
            buttonCell = cellFactory.call(null)
            selectionModel.select(appInfo.selectedFromLanguage)
            setOnAction {
                saveAppInfo(
                    getAppInfo().copy(
                        selectedFromLanguage = selectionModel.selectedItem
                    )
                )
            }
        }
        val translateToComboBox = ComboBox(
            FXCollections.observableArrayList(ApplicationLanguage.entries)
        ).apply {
            cellFactory = javafx.util.Callback {
                object : ListCell<ApplicationLanguage>() {
                    override fun updateItem(item: ApplicationLanguage?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (item == null || empty) null else item.name
                    }
                }
            }
            buttonCell = cellFactory.call(null)
            selectionModel.select(appInfo.selectedToLanguage)
            setOnAction {
                saveAppInfo(
                    getAppInfo().copy(
                        selectedToLanguage = selectionModel.selectedItem
                    )
                )
            }
        }
        val langContainer = HBox(4.0).apply {
            alignment = Pos.CENTER
            padding = Insets(0.0, 20.0, 0.0, 20.0)
            translateFromComboBox.prefWidthProperty().bind(widthProperty().multiply(0.3))
            translateToComboBox.prefWidthProperty().bind(widthProperty().multiply(0.3))
            children.addAll(translateFromComboBox, translateToComboBox)
        }
        children.add(langContainer)

        addTitleLabel("Google CLoud token:")

        addTextFieldWithButtons(
            fieldText = appInfo.googleCloudToken,
            onSave = {
                saveAppInfo(
                    getAppInfo().copy(
                        googleCloudToken = it
                    )
                )
            }
        )

        addTitleLabel("LM Studio IP Address / Port")

        addTextFieldWithButtons(
            fieldText = appInfo.lmStudioConfig.ip,
            onSave = {
                val appInfo = getAppInfo()
                saveAppInfo(
                    appInfo.copy(
                        lmStudioConfig = appInfo.lmStudioConfig.copy(
                            ip = it
                        ),
                    )
                )
            }
        )

        addTextFieldWithButtons(
            fieldText = appInfo.lmStudioConfig.port,
            onSave = {
                val appInfo = getAppInfo()
                saveAppInfo(
                    appInfo.copy(
                        lmStudioConfig = appInfo.lmStudioConfig.copy(
                            port = it
                        ),
                    )
                )
            }
        )

        addButton(
            title = "Update LM Studio models",
            bgColor = COLOUR_GREEN,
            onClicked = {
                val appInfo = getAppInfo()
                getLmStudioModels(
                    ip = appInfo.lmStudioConfig.ip,
                    port = appInfo.lmStudioConfig.port,
                )
            }
        )

        addTitleLabel("Ollama IP Address / Port")

        addTextFieldWithButtons(
            fieldText = appInfo.ollamaConfig.port,
            onSave = {
                val appInfo = getAppInfo()
                saveAppInfo(
                    appInfo.copy(
                        ollamaConfig = appInfo.ollamaConfig.copy(
                            port = it
                        )
                    )
                )
            }
        )

        addTextFieldWithButtons(
            fieldText = appInfo.ollamaConfig.ip,
            onSave = {
                val appInfo = getAppInfo()
                saveAppInfo(
                    appInfo.copy(
                        ollamaConfig = appInfo.ollamaConfig.copy(
                            ip = it
                        ),
                    )
                )
            }
        )

        addButton(
            title = "Update Ollama models",
            bgColor = COLOUR_GREEN,
            onClicked = {
                getOllamaModels(
                    ip = appInfo.ollamaConfig.ip,
                    port = appInfo.ollamaConfig.port,
                )
            }
        )

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

    private fun getLmStudioModels(
        ip: String,
        port: String,
    ) {
        backgroundThreadScope.launch {
            val result = localNetworkRepository.getLmStudioModels(
                ip = ip,
                port = port,
            )
            if (!result.isSuccess) {
                log.error("getLmStudioModels result error: ${result.exceptionOrNull()}")
                showAlert(
                    alertTitle = "Error",
                    alertContent = result.exceptionOrNull()?.message ?: ""
                )
                return@launch
            }
            val resultData = result.getOrNull()
            if (resultData.isNullOrEmpty()) {
                log.error("getLmStudioModels resultData == null")
                showAlert(
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

    private fun getOllamaModels(
        ip: String,
        port: String,
    ){
        backgroundThreadScope.launch {
            val result = localNetworkRepository.getOllamaModels(
                ip = ip,
                port = port,
            )
            if (!result.isSuccess) {
                log.error("getOllamaModels result error: ${result.exceptionOrNull()}")
                showAlert(
                    alertTitle = "Error",
                    alertContent = result.exceptionOrNull()?.message ?: ""
                )
                return@launch
            }
            val resultData = result.getOrNull()
            if (resultData.isNullOrEmpty()) {
                log.error("getOllamaModels resultData == null")
                showAlert(
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
