package app

import PROMPT_ANSWER_TO_QUESTIONS
import PROMPT_TRANSLATE_TEXT
import defaultApplicationInfo
import di.allModules
import extensions.showAlertWithAction
import ui.MessagesTab
import ui.SettingsTab
import javafx.application.Application
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.stage.Screen
import javafx.stage.Stage
import models.domain.PromptModel
import models.domain.VoiceRecognizerEngineEnum
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ui.PromptTab
import org.koin.core.context.startKoin
import repository.PreferencesRepository
import ui.ConsoleTab
import utils.VoskVoiceRecognizer
import java.awt.Desktop
import java.net.URI

class DialogApplication : Application(), KoinComponent {

    private val voskVoiceRecognizer: VoskVoiceRecognizer by inject()
    private val preferencesRepository: PreferencesRepository by inject()

    private val messagesTab: MessagesTab by inject()
    private val promptTab: PromptTab by inject()
    private val settingsTab: SettingsTab by inject()
    private val consoleTab: ConsoleTab by inject()

    companion object {
        var ownerStage: Stage? = null
    }

    override fun start(primaryStage: Stage) {

        startKoin {
            modules(allModules)
        }

//        preferencesRepository.clear()

        ownerStage = primaryStage
        primaryStage.isMaximized = true

        val appInfo = preferencesRepository.getAppInfo()

        if (appInfo.prompt.prompt.isBlank()) {
            preferencesRepository.saveAppInfo(
                preferencesRepository.getAppInfo().copy(
                    prompt = PromptModel(
                        name = "Translate text",
                        prompt = PROMPT_TRANSLATE_TEXT,
                    )
                )
            )
        }

//        preferencesRepository.saveAppInfo(
//            preferencesRepository.getAppInfo().copy(
//                promptsMap = defaultApplicationInfo.promptsMap,
//                prompt = PromptModel(
//                    name =  "Translate text",
//                    prompt = PROMPT_TRANSLATE_TEXT,
//                )
//            )
//        )

        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tabs.addAll(
                Tab("Messages", messagesTab.content),
                Tab("Prompt", ScrollPane(promptTab.content).apply {
                    fitToWidthProperty().set(true)
                    fitToHeightProperty().set(true)
                    isPannable = true
                }),
                Tab("Settings", ScrollPane(settingsTab.content).apply {
                    fitToWidthProperty().set(true)
                    fitToHeightProperty().set(true)
                    isPannable = true
                }),
                Tab("Console", ScrollPane(consoleTab.content).apply {
                    fitToWidthProperty().set(true)
                    fitToHeightProperty().set(true)
                    isPannable = true
                }),
            )
            selectionModel.select(appInfo.lastOpenedTab)
            selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
                preferencesRepository.saveAppInfo(
                    preferencesRepository.getAppInfo().copy(
                        lastOpenedTab = newValue.toInt()
                    )
                )
            }
        }

        val screenBounds: Rectangle2D =
            Screen.getScreens().let { if (it.size > 1) it[1] else it[0] }.visualBounds

        val scene = Scene(tabPane, screenBounds.width, screenBounds.height).apply {
            stylesheets.add(javaClass.getResource("/styles/styles.css")?.toExternalForm())
        }

        primaryStage.apply {
            x = screenBounds.minX
            y = screenBounds.minY
            width = screenBounds.width
            height = screenBounds.height
            title = "Dialog Application"
            this.scene = scene
            show()
        }
        if (appInfo.voiceRecognizer == VoiceRecognizerEngineEnum.VOSK) {
            if (appInfo.voskModelPath.isNotEmpty()) {
                voskVoiceRecognizer.init(appInfo.voskModelPath)
            } else {
                ownerStage?.showAlertWithAction(
                    alertTitle = "Voice recognizer error",
                    alertContent = "To use voice recognition, you must download the model for recognition, unzip it to disk and select the folder with it.",
                    positiveButtonText = "Show models",
                    onPositive = {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(URI("https://alphacephei.com/vosk/models"))
                        } else {
                            println("Desktop is not supported")
                        }
                    }
                )
            }
        }
    }

}
