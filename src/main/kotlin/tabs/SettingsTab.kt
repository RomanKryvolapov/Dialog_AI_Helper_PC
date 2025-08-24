package tabs

import COLOUR_GREEN
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
import kotlinx.coroutines.launch
import models.common.ApplicationLanguage
import models.common.GoogleAiModelsEnum
import models.domain.LlmModel
import models.domain.LlmModelEngine
import repository.LocalNetworkRepository
import viewModelScope
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

object SettingsTab : BaseTab() {

    const val WINDOW_BACKGROUND_COLOUR = "#323232"

    val currentToggleGroup = ToggleGroup()
    private val devices: List<Mixer.Info> = getAvailableInputDevices()

    var lmStudioModelsBox: ComboBox<LlmModel>? = null

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val appInfo = getAppInfo()

        addTitleLabel("Audio device for voice recognition:")

        if (devices.isEmpty()) {
            addLabel("Not found")
        } else {
            val lastSelected = devices.firstOrNull {
                appInfo.lastSelectedDevice == it.name
            } ?: devices.first()
            addComboBox(
                items = devices,
                selectedItem = lastSelected,
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

        lmStudioModelsBox = addComboBox(
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

        addTitleLabel("LM Studio Port")

        addTextFieldWithButtons(
            fieldText = appInfo.lmStudioPort,
            onSave = {
                saveAppInfo(
                    getAppInfo().copy(
                        lmStudioPort = it
                    )
                )
            }
        )
        addButton(
            title = "Update models",
            bgColor = COLOUR_GREEN,
            onClicked = {
                getLmStudioModels(
                    port = getAppInfo().lmStudioPort
                )
            }
        )

    }

    private fun getModelsList(): List<LlmModel> {
        return buildList {
            getAppInfo().lmStudioModels.forEach {
                add(
                    LlmModel(
                        id = it.id,
                        description = "LM Studio Local: ${it.id}",
                        engine = LlmModelEngine.LOCALHOST,
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

    private fun getLmStudioModels(port: String) {
        viewModelScope.launch {
            val models = LocalNetworkRepository.getLmStudioModels(
                port = port
            )
            saveAppInfo(
                getAppInfo().copy(
                    lmStudioModels = models
                )
            )
            lmStudioModelsBox?.items = FXCollections.observableArrayList(getModelsList())
            val selected = getAppInfo().selectedModel
            models.firstOrNull {
                it.id == selected.id
            }?.let {
                lmStudioModelsBox?.selectionModel?.select(it)
            }

        }
    }

    private fun getAvailableInputDevices(): List<Mixer.Info> {
        val mixers = AudioSystem.getMixerInfo()
        return mixers.filter {
            AudioSystem.getMixer(it).targetLineInfo.any { info -> info.lineClass == TargetDataLine::class.java }
        }
    }

    fun getSelectedDevice(): Mixer.Info? =
        currentToggleGroup.selectedToggle?.userData as? Mixer.Info


}
