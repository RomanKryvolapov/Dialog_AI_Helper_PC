package tabs

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.PopupControl.USE_COMPUTED_SIZE
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.*
import javafx.scene.paint.Color
import java.util.prefs.Preferences
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine
import models.TranslateWithGoogleAiModelsEnum

object SettingsTab {

    const val TITLE_TEXT_COLOUR = "#FFFFFF"
    const val ELEMENTS_TEXT_COLOUR = "#999999"
    const val WINDOW_BACKGROUND_COLOUR = "#222222"
    private const val RADIO_FONT_SIZE = 16
    private const val LABEL_FONT_SIZE = 20
    private const val DROPDOWN_FONT_SIZE = 16

    private val prefs: Preferences = Preferences.userNodeForPackage(SettingsTab::class.java)
    private const val KEY_LAST_DEVICE = "lastSelectedDevice"
    private const val KEY_LAST_MODEL = "lastSelectedModel"

    val currentToggleGroup = ToggleGroup()
    private val devices: List<Mixer.Info> = getAvailableInputDevices()

    private val modelComboBox: ComboBox<TranslateWithGoogleAiModelsEnum> = ComboBox(
        FXCollections.observableArrayList(TranslateWithGoogleAiModelsEnum.entries)
    )

    val content: VBox = VBox(10.0).apply {
        padding = Insets(50.0)
        alignment = Pos.TOP_LEFT
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val labelAudioDevice = Label("Select audio device:").apply {
            style = "-fx-text-fill: $TITLE_TEXT_COLOUR; -fx-font-size: ${LABEL_FONT_SIZE}px;"
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
        }
        children.add(labelAudioDevice)

        // VBox для RadioButtons
        val radioVBox = VBox(5.0).apply {
            alignment = Pos.TOP_LEFT
        }

        var maxRadioWidth = 0.0

        if (devices.isEmpty()) {
            radioVBox.children.add(Label("No input audio devices found").apply {
                style = "-fx-text-fill: $ELEMENTS_TEXT_COLOUR; -fx-font-size: ${LABEL_FONT_SIZE}px;"
            })
        } else {
            val lastDeviceName = prefs.get(KEY_LAST_DEVICE, null)

            devices.forEachIndexed { index, mixerInfo ->
                val radioButton = RadioButton("[$index] ${mixerInfo.name}").apply {
                    toggleGroup = currentToggleGroup
                    userData = mixerInfo
                    style = "-fx-text-fill: $ELEMENTS_TEXT_COLOUR; -fx-font-size: ${RADIO_FONT_SIZE}px;"
                    if (mixerInfo.name == lastDeviceName) isSelected = true
                    setOnAction { prefs.put(KEY_LAST_DEVICE, mixerInfo.name) }
                }
                radioVBox.children.add(radioButton)

                // Вычисляем ширину для контейнера
                radioButton.applyCss()
                radioButton.layout()
                val width = radioButton.width
                if (width > maxRadioWidth) {
                    maxRadioWidth = width
                }
            }

            if (currentToggleGroup.selectedToggle == null && devices.isNotEmpty()) {
                currentToggleGroup.selectToggle(currentToggleGroup.toggles[0])
                prefs.put(KEY_LAST_DEVICE, devices[0].name)
            }
        }

        val radioContainer = HBox(radioVBox).apply {
            alignment = Pos.CENTER
            padding = Insets(5.0)
        }

        radioVBox.prefWidth = USE_COMPUTED_SIZE
        radioVBox.maxWidth = Region.USE_PREF_SIZE

        children.add(radioContainer)

        // Model Label
        val labelModel = Label("Select AI model:").apply {
            style = "-fx-text-fill: $TITLE_TEXT_COLOUR; -fx-font-size: ${LABEL_FONT_SIZE}px;"
            alignment = Pos.CENTER
            maxWidth = Double.MAX_VALUE
        }
        children.add(labelModel)

        // Model ComboBox
        modelComboBox.cellFactory = javafx.util.Callback {
            object : ListCell<TranslateWithGoogleAiModelsEnum>() {
                override fun updateItem(item: TranslateWithGoogleAiModelsEnum?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (item == null || empty) null
                    else "${item.type} (Requests/min=${item.limitRequestsInMinute}, Requests/day=${item.limitRequestsInDay}, Tokens/min=${item.limitTokensInMinute})"
                    style = "-fx-font-size: ${DROPDOWN_FONT_SIZE}px;"
                }
            }
        }
        modelComboBox.buttonCell = object : ListCell<TranslateWithGoogleAiModelsEnum>() {
            override fun updateItem(item: TranslateWithGoogleAiModelsEnum?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (item == null || empty) null
                else "${item.type} (Requests/min=${item.limitRequestsInMinute}, Requests/day=${item.limitRequestsInDay}, Tokens/min=${item.limitTokensInMinute})"
                style = "-fx-font-size: ${DROPDOWN_FONT_SIZE}px;"
            }
        }

        val lastModelType = prefs.get(KEY_LAST_MODEL, null)
        modelComboBox.selectionModel.select(
            TranslateWithGoogleAiModelsEnum.entries.firstOrNull { it.type == lastModelType }
                ?: TranslateWithGoogleAiModelsEnum.GEMINI_2_5_FLASH
        )

        modelComboBox.setOnAction {
            val selected = modelComboBox.selectionModel.selectedItem
            prefs.put(KEY_LAST_MODEL, selected.type)
        }

        val comboBoxContainer = HBox(modelComboBox).apply {
            alignment = Pos.CENTER
            padding = Insets(5.0)
            modelComboBox.prefWidthProperty().bind(widthProperty().multiply(0.4))
        }

        children.add(comboBoxContainer)
    }

    private fun getAvailableInputDevices(): List<Mixer.Info> {
        val mixers = AudioSystem.getMixerInfo()
        return mixers.filter { mixerInfo ->
            val mixer = AudioSystem.getMixer(mixerInfo)
            mixer.targetLineInfo.any { it.lineClass == TargetDataLine::class.java }
        }
    }

    fun getSelectedDevice(): Mixer.Info? =
        currentToggleGroup.selectedToggle?.userData as? Mixer.Info

    fun getSelectedModel(): TranslateWithGoogleAiModelsEnum =
        modelComboBox.selectionModel.selectedItem
}
