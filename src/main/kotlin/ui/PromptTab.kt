package ui

import SOURCE_TEXT
import extensions.addComboBox
import extensions.addLabel
import extensions.addTextFieldWithCopyPasteActionButtons
import extensions.addTitleLabel
import javafx.geometry.Insets
import javafx.scene.control.TextInputControl
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import models.domain.PromptModel
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import ui.base.BaseTab

class PromptTab(
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
    }

    private val log = LoggerFactory.getLogger("PromptTab")

    var promptTextInput: TextInputControl? = null

    private var selected: PromptModel? = null

    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        val appInfo = getAppInfo()

        selected = appInfo.prompt

        addTitleLabel("Prompts:")

        addComboBox(
            items = appInfo.promptsMap.values.toList(),
            selectedItem = appInfo.prompt,
            toStringFn = { prompt ->
                prompt.name
            },
            onSelected = { prompt ->
                selected = prompt
                promptTextInput?.text = prompt.prompt
            }
        )

        addTitleLabel("Prompt:")

        addLabel("Will be replaced with actual values: $SOURCE_TEXT")

        promptTextInput = addTextFieldWithCopyPasteActionButtons(
            fieldText = appInfo.prompt.prompt,
            lines = 36,
            onClicked = {
                val selected = selected ?: return@addTextFieldWithCopyPasteActionButtons
                saveAppInfo(
                    appInfo.copy(
                        prompt = selected.copy(
                            prompt = it
                        ),
                    )
                )
            }
        )
    }

}