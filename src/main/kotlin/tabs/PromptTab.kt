package tabs

import javafx.geometry.Insets
import javafx.scene.control.TextArea
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import repository.PreferencesRepository
import utils.addLabel
import utils.addTitleLabel
import utils.createTextFieldRow

object PromptTab {

    const val WINDOW_BACKGROUND_COLOUR = "#323232"

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        addTitleLabel("Prompt:")
        addLabel("Will be replaced with actual values: TRANSLATE_FROM_LANGUAGE, TRANSLATE_TO_LANGUAGE, TRANSLATE_TEXT")
        val promptField = TextArea().apply {
            isWrapText = true
            prefRowCount = 36
            text = PreferencesRepository.getPrompt()
        }
        createTextFieldRow(
            field = promptField,
            onSave = {
                PreferencesRepository.setPrompt(it)
            }
        )
    }

}