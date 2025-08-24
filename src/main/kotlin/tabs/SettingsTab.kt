package tabs

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.paint.Color
import models.ApplicationLanguage
import models.TranslateWithGoogleAiModelsEnum
import repository.PreferencesRepository
import utils.addTitleLabel
import utils.addComboBoxSettingRowWithLabel
import utils.createTextFieldRow
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

object SettingsTab {

    const val WINDOW_BACKGROUND_COLOUR = "#323232"

    val currentToggleGroup = ToggleGroup()
    private val devices: List<Mixer.Info> = getAvailableInputDevices()

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        addTitleLabel("Audio device for voice recognition:")
        val radioVBox = VBox(4.0)
        if (devices.isEmpty()) {
            radioVBox.children.add(Label("No input audio devices found"))
        } else {
            val lastDeviceName = PreferencesRepository.getLastSelectedDevice()
            devices.forEachIndexed { index, mixerInfo ->
                val radioButton = RadioButton("[$index] ${mixerInfo.name}").apply {
                    toggleGroup = currentToggleGroup
                    userData = mixerInfo
                    if (mixerInfo.name == lastDeviceName) isSelected = true
                    setOnAction { PreferencesRepository.setLastSelectedDevice(mixerInfo.name) }
                }
                radioVBox.children.add(radioButton)
            }
            if (currentToggleGroup.selectedToggle == null) {
                currentToggleGroup.selectToggle(currentToggleGroup.toggles.firstOrNull())
                PreferencesRepository.setLastSelectedDevice(devices.firstOrNull()?.name ?: "")
            }
        }
        children.add(HBox(radioVBox).apply {
            alignment = Pos.CENTER
        })

        addComboBoxSettingRowWithLabel(
            title = "AI model:",
            items = TranslateWithGoogleAiModelsEnum.entries,
            selectedItem = PreferencesRepository.getSelectedModel(),
            toStringFn = {
                "${it.type} (Requests/min=${it.limitRequestsInMinute}, Requests/day=${it.limitRequestsInDay}, Tokens/min=${it.limitTokensInMinute})"
            },
            onSelected = PreferencesRepository::setSelectedModel
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
            selectionModel.select(PreferencesRepository.getSelectedFromLanguage())
            setOnAction {
                PreferencesRepository.setSelectedFromLanguage(selectionModel.selectedItem)
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
            selectionModel.select(PreferencesRepository.getSelectedToLanguage())
            setOnAction {
                PreferencesRepository.setSelectedToLanguage(selectionModel.selectedItem)
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

        val tokenField = TextField().apply {
            text = PreferencesRepository.getGoogleCloudToken()
        }
        createTextFieldRow(
            field = tokenField,
            onSave = {
                PreferencesRepository.setGoogleCloudToken(it)
            }
        )
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
