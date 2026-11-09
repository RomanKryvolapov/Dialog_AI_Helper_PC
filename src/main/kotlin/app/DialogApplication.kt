package app

import OVERWRITE_PROMPTS_FROM_CODE
import PROMPT_TRANSLATE_TEXT
import defaultApplicationInfo
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
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration as FxDuration
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.PromptModel
import models.domain.resolveLlmModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import repository.PreferencesRepository
import ui.ConsoleTab
import ui.MessagesTab
import ui.PromptTab
import ui.SettingsTab
import utils.AudioChunker
import utils.TokenUsageTracker
import java.lang.management.ManagementFactory

class DialogApplication : Application(), KoinComponent {

    private val preferencesRepository: PreferencesRepository by inject()
    private val audioChunker: AudioChunker by inject()

    private val messagesTab: MessagesTab by inject()
    private val promptTab: PromptTab by inject()
    private val settingsTab: SettingsTab by inject()
    private val consoleTab: ConsoleTab by inject()
    private val tokenUsageTracker: TokenUsageTracker by inject()

    companion object {
        var ownerStage: Stage? = null
        private const val MESSAGES_TAB_INDEX = 0
        /** The tab header row's height, as styles.css sets it. */
        private const val TOP_BAR_HEIGHT = 64.0

        /** 950, 12.3k, 1.2M — short enough for the top bar. */
        internal fun compactCount(count: Long): String = when {
            count < 1_000 -> count.toString()
            count < 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fk", count / 1_000.0)
            else -> String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0)
        }

        internal fun tokensTooltip(totals: TokenUsageTracker.Totals): String = buildString {
            appendLine("Tokens this dialog has used since Clear dialog, translations and conversation notes,")
            append("as the providers reported them: in ${totals.input}, out ${totals.output}, ${totals.answers} answers")
            totals.byModel.forEach { (id, usage) ->
                val title = resolveLlmModel(id)?.title ?: id
                appendLine()
                append("$title: in ${usage.input}, out ${usage.output}, ${usage.answers} answers")
            }
            if (totals.unreported > 0) {
                appendLine()
                append("${totals.unreported} answers came without a token count")
            }
        }
    }

    override fun start(primaryStage: Stage) {
        startKoin {
            modules(allModules)
        }

        ownerStage = primaryStage
        primaryStage.isMaximized = true

        if (OVERWRITE_PROMPTS_FROM_CODE) {
            val current = preferencesRepository.getAppInfo()
            val codePrompts = defaultApplicationInfo.promptsMap
            preferencesRepository.saveAppInfo(
                current.copy(
                    promptsMap = codePrompts,
                    prompt = codePrompts[current.prompt.name] ?: defaultApplicationInfo.prompt,
                )
            )
        }

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
                Tab("Settings", settingsTab.content),
            )
            selectionModel.select(appInfo.lastOpenedTab.coerceIn(0, tabs.size - 1))
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
            prefWidth = 110.0
            prefHeight = 18.0
        }

        val ramLabel = Label("RAM").apply {
            style = overlayLabelStyle
        }

        val ramBar = ProgressBar(0.0).apply {
            prefWidth = 110.0
            prefHeight = 18.0
        }

        val ramValueLabel = Label("").apply {
            style = overlayLabelStyle
        }

        val meters = HBox(12.0, micLabel, micBar, ramLabel, ramBar, ramValueLabel).apply {
            alignment = Pos.CENTER_LEFT
            isMouseTransparent = true
        }

        // The dialog's buttons act on the Messages tab only; on the other tabs they stay in view, disabled.
        messagesTab.headerControls.disableProperty()
            .bind(tabPane.selectionModel.selectedIndexProperty().isNotEqualTo(MESSAGES_TAB_INDEX))

        // The top bar sits in the tab header row, right of the tab names: the dialog's buttons, then the
        // microphone and memory meters.
        // No left padding: the last tab's own right inset is the gap, the same as between the tabs.
        // Not part of the mouse-transparent meters: its tooltip breaks the count down.
        val tokensLabel = Label().apply { style = overlayLabelStyle }

        val topBar = HBox(24.0, messagesTab.headerControls, meters, tokensLabel).apply {
            alignment = Pos.CENTER_LEFT
            minHeight = TOP_BAR_HEIGHT
            prefHeight = TOP_BAR_HEIGHT
            maxHeight = TOP_BAR_HEIGHT
            maxWidth = Region.USE_PREF_SIZE
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
            tokenUsageTracker.totals.collect { totals ->
                tokensLabel.text = "Tokens ${compactCount(totals.total)}"
                tokensLabel.tooltip = Tooltip(tokensTooltip(totals))
            }
        }

        mainThreadScope.launch {
            audioChunker.noiceLevelFlow.collect { level ->
                micBar.progress = level.coerceIn(0, 100) / 100.0
            }
        }

        val root = StackPane(tabPane, topBar)

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
                topBar.translateXProperty().bind(headersRegion.widthProperty())
            } else {
                System.err.println("[DialogApplication] .headers-region not found, top bar pinned to left edge")
            }
        }
    }

}
