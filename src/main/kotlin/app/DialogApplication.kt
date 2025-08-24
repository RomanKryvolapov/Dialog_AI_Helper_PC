package app

import PROMPT_FULL_SIZE
import repository.PreferencesRepository
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
import tabs.PromptTab

class DialogApplication : Application() {

    override fun start(primaryStage: Stage) {

//        primaryStage.isFullScreen = true
        primaryStage.isMaximized = true

        val appInfo = PreferencesRepository.getAppInfo()

        if (appInfo.prompt.isBlank()) {
            PreferencesRepository.saveAppInfo(
                PreferencesRepository.getAppInfo().copy(
                    prompt = PROMPT_FULL_SIZE
                )
            )
        }

        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tabs.addAll(
                Tab("Messages", MessagesTab.content),
                Tab("Prompt", PromptTab.content),
                Tab("Settings", ScrollPane(SettingsTab.content).apply {
                    fitToWidthProperty().set(true)
                    fitToHeightProperty().set(true)
                    isPannable = true
                })
            )
            selectionModel.select(appInfo.lastOpenedTab)
            selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
                PreferencesRepository.saveAppInfo(
                    PreferencesRepository.getAppInfo().copy(
                        lastOpenedTab = newValue.toInt()
                    )
                )
            }
        }

        MessagesTab.ownerStage = primaryStage

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
    }
}
