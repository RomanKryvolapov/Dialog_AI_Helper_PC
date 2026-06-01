package app

import PROMPT_TRANSLATE_TEXT
import com.sun.management.OperatingSystemMXBean
import di.allModules
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration as FxDuration
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.PromptModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import repository.PreferencesRepository
import ui.ConsoleTab
import ui.MessagesTab
import ui.PromptTab
import ui.SettingsTab
import utils.AppStatus
import utils.AudioChunker
import java.lang.management.ManagementFactory

class DialogApplication : Application(), KoinComponent {

    private val preferencesRepository: PreferencesRepository by inject()
    private val audioChunker: AudioChunker by inject()

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

        val overlayLabelStyle = "-fx-text-fill: #e6e6e6; -fx-font-size: 20px; -fx-pref-height: 32px; -fx-min-height: 32px; -fx-max-height: 32px;"

        val micLabel = Label("Mic").apply {
            style = overlayLabelStyle
        }

        val micBar = ProgressBar(0.0).apply {
            prefWidth = 140.0
            prefHeight = 18.0
        }

        val ramLabel = Label("RAM").apply {
            style = overlayLabelStyle
        }

        val ramBar = ProgressBar(0.0).apply {
            prefWidth = 140.0
            prefHeight = 18.0
        }

        val ramValueLabel = Label("").apply {
            style = overlayLabelStyle
        }

        val topRightBar = HBox(12.0, micLabel, micBar, ramLabel, ramBar, ramValueLabel).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(18.0, 0.0, 0.0, 16.0)
            isMouseTransparent = true
            maxWidth = Region.USE_PREF_SIZE
            maxHeight = Region.USE_PREF_SIZE
            StackPane.setAlignment(this, Pos.TOP_LEFT)
        }

        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
        val updateRam = {
            val total = osBean.totalMemorySize
            val free = osBean.freeMemorySize
            val used = total - free
            if (total > 0) {
                ramBar.progress = used.toDouble() / total.toDouble()
            }
            val gb = 1024.0 * 1024.0 * 1024.0
            ramValueLabel.text = String.format("%.1f / %.1f GB", free / gb, total / gb)
        }
        updateRam()
        Timeline(KeyFrame(FxDuration.seconds(2.0), { updateRam() })).apply {
            cycleCount = Timeline.INDEFINITE
            play()
        }

        mainThreadScope.launch {
            audioChunker.noiceLevelFlow.collect { level ->
                micBar.progress = level.coerceIn(0, 100) / 100.0
            }
        }

        val root = StackPane(tabPane, topRightBar)

        val screenBounds: Rectangle2D =
            Screen.getScreens().let { if (it.size > 1) it[1] else it[0] }.visualBounds

        val stylesheetUrl = DialogApplication::class.java.getResource("/styles/styles.css")?.toExternalForm()
        if (stylesheetUrl == null) {
            System.err.println("[DialogApplication] /styles/styles.css not found on classpath")
        }

        val scene = Scene(root, screenBounds.width, screenBounds.height).apply {
            stylesheetUrl?.let { stylesheets.add(it) }
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

        javafx.application.Platform.runLater {
            val headersRegion = tabPane.lookup(".tab-header-area > .headers-region") as? Region
            if (headersRegion != null) {
                topRightBar.translateXProperty().bind(headersRegion.widthProperty())
            } else {
                System.err.println("[DialogApplication] .headers-region not found, overlay pinned to left edge")
            }
        }
    }

}
