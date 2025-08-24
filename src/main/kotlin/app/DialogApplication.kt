package app

import PROMPT_FULL_SIZE
import di.allModules
import tabs.MessagesTab
import tabs.SettingsTab
import javafx.application.Application
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.stage.Screen
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tabs.PromptTab
import org.koin.core.context.startKoin
import repository.PreferencesRepository
import utils.VoskRecognizer

class DialogApplication : Application(), KoinComponent {

    private val voskRecognizer: VoskRecognizer by inject()
    private val preferencesRepository: PreferencesRepository by inject()

    private val messagesTab: MessagesTab by inject()
    private val promptTab: PromptTab by inject()
    private val settingsTab: SettingsTab by inject()

    companion object{
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

        if (appInfo.prompt.isBlank()) {
            preferencesRepository.saveAppInfo(
                preferencesRepository.getAppInfo().copy(
                    prompt = PROMPT_FULL_SIZE
                )
            )
        }

        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tabs.addAll(
                Tab("Messages", messagesTab.content),
                Tab("Prompt", promptTab.content),
                Tab("Settings", ScrollPane(settingsTab.content).apply {
                    fitToWidthProperty().set(true)
                    fitToHeightProperty().set(true)
                    isPannable = true
                })
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
        voskRecognizer.init(appInfo.voskModel)
    }
}
