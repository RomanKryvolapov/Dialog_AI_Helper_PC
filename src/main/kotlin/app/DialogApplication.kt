package app

import tabs.MessagesTab
import tabs.SettingsTab
import javafx.application.Application
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.stage.Screen
import javafx.stage.Stage

class DialogApplication : Application() {

    override fun start(primaryStage: Stage) {

        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tabs.addAll(
                Tab("Messages", MessagesTab.content),
                Tab("Settings", SettingsTab.content)
            )
        }

        MessagesTab.ownerStage = primaryStage

        val screenBounds: Rectangle2D = Screen.getScreens().let { if (it.size > 1) it[1] else it[0] }.visualBounds
        val scene = Scene(tabPane, screenBounds.width, screenBounds.height)

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