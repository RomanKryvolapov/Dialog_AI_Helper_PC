package tabs

import TRANSLATE_FROM_LANGUAGE
import TRANSLATE_TEXT
import TRANSLATE_TO_LANGUAGE
import extensions.addLabel
import extensions.addTextFieldWithButtons
import extensions.addTitleLabel
import javafx.geometry.Insets
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import org.slf4j.LoggerFactory
import repository.PreferencesRepository

class PromptTab(
    preferencesRepository: PreferencesRepository
) : BaseTab(
    preferencesRepository = preferencesRepository,
) {

    companion object {
        const val WINDOW_BACKGROUND_COLOUR = "#323232"
    }

    private val log = LoggerFactory.getLogger("PromptTabTag")


    val content: VBox = VBox(4.0).apply {
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        background = Background(BackgroundFill(Color.web(WINDOW_BACKGROUND_COLOUR), null, null))

        addTitleLabel("Prompt:")
        addLabel("Will be replaced with actual values: $TRANSLATE_FROM_LANGUAGE, $TRANSLATE_TO_LANGUAGE, $TRANSLATE_TEXT")
        val appInfo = getAppInfo()
        addTextFieldWithButtons(
            fieldText = appInfo.prompt,
            lines = 36,
            onSave = {
                saveAppInfo(
                    appInfo.copy(
                        prompt = it
                    )
                )
            }
        )
    }

}