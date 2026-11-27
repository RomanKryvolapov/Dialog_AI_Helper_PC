package app

import com.sun.management.OperatingSystemMXBean
import di.allModules
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Application
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.geometry.Rectangle2D
import javafx.scene.Scene
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.text.Text
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.util.Duration as FxDuration
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.ApplicationInfo
import models.domain.resolveLlmModel
import ui.base.ScaledPane
import ui.base.scalePopupsLike
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import repository.PreferencesRepository
import ui.ConsoleTab
import ui.MessagesTab
import ui.QuestionsTab
import ui.SettingsTab
import ui.WindowPresence
import utils.AudioChunker
import utils.ActiveMethod
import utils.TokenUsageTracker
import java.lang.management.ManagementFactory

class DialogApplication : Application(), KoinComponent {

    private val preferencesRepository: PreferencesRepository by inject()
    private val audioChunker: AudioChunker by inject()

    private val messagesTab: MessagesTab by inject()
    private val questionsTab: QuestionsTab by inject()
    private val settingsTab: SettingsTab by inject()
    private val consoleTab: ConsoleTab by inject()
    private val tokenUsageTracker: TokenUsageTracker by inject()
    private val activeMethod: ActiveMethod by inject()
    private val windowPresence: WindowPresence by inject()

    companion object {
        var ownerStage: Stage? = null
        private const val MESSAGES_TAB_INDEX = 0
        private const val QUESTIONS_TAB_INDEX = 1
        /** The tab header row's height, as styles.css sets it. */
        private const val TOP_BAR_HEIGHT = 64.0
        private const val BAR_SPACING = 24.0
        private const val METER_SPACING = 12.0
        private const val METER_BAR_WIDTH = 110.0
        /** Kept clear between the top bar and the window's right edge. */
        private const val BAR_RIGHT_GAP = 12.0
        /** The method label keeps at least this much ("Method 1: " and the start of its name) before anything else goes. */
        private const val METHOD_MIN_WIDTH = 220.0

        /**
         * How many of the [giveWay] widths (the first gives way first) must go so that the rest fits into
         * [available] beside [alwaysShown].
         */
        internal fun hiddenCount(available: Double, alwaysShown: Double, giveWay: List<Double>): Int {
            var needed = alwaysShown + giveWay.sum()
            var hidden = 0
            while (hidden < giveWay.size && needed > available) {
                needed -= giveWay[hidden]
                hidden++
            }
            return hidden
        }

        /** The method label when the dialog has left method 1. */
        private const val RESERVE_COLOUR = "#E0A030"

        /** 950, 12.3k, 1.2M — short enough for the top bar. */
        internal fun compactCount(count: Long): String = when {
            count < 1_000 -> count.toString()
            count < 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fk", count / 1_000.0)
            else -> String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0)
        }

        /** The top bar's count: what the requests sent (in) and what the models wrote back (out), apart. */
        internal fun tokensText(totals: TokenUsageTracker.Totals): String =
            "Tokens in ${compactCount(totals.input)} · out ${compactCount(totals.output)}"

        internal fun tokensTooltip(totals: TokenUsageTracker.Totals): String = buildString {
            appendLine("Tokens this dialog has used since Clear dialog, translations, conversation summaries and questions,")
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

        val appInfo = preferencesRepository.getAppInfo()

        val tabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            tabs.addAll(
                Tab("Messages", messagesTab.content),
                Tab("Context", questionsTab.content),
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

        // Shown whole or not at all (see fitTopBar below): never cut short with an ellipsis.
        fun barLabel(text: String = "") = Label(text).apply {
            style = overlayLabelStyle
            minWidth = Region.USE_PREF_SIZE
        }

        fun meterBar() = ProgressBar(0.0).apply {
            prefWidth = METER_BAR_WIDTH
            minWidth = METER_BAR_WIDTH
            prefHeight = 18.0
        }

        val micLabel = barLabel("Mic")
        val micBar = meterBar()
        val ramLabel = barLabel("RAM")
        val ramBar = meterBar()
        val ramValueLabel = barLabel()

        // A part of the app the top bar switches on and off; a change made elsewhere (Settings → Questions) is followed.
        fun switchBox(
            title: String,
            read: (ApplicationInfo) -> Boolean,
            write: (ApplicationInfo, Boolean) -> ApplicationInfo,
        ) = CheckBox(title).apply {
            style = overlayLabelStyle
            minWidth = Region.USE_PREF_SIZE
            isSelected = read(preferencesRepository.getAppInfo())
            selectedProperty().addListener { _, _, on ->
                val info = preferencesRepository.getAppInfo()
                if (read(info) != on) preferencesRepository.saveAppInfo(write(info, on))
            }
            mainThreadScope.launch {
                preferencesRepository.appInfoFlow.collect { info -> if (isSelected != read(info)) isSelected = read(info) }
            }
        }

        // Off: phrases are heard and shown but not sent for translation.
        val translationBox = switchBox("Translation", { it.translationEnabled }) { info, on -> info.copy(translationEnabled = on) }

        // The Questions mode on or off (Settings → Questions holds the rest of it).
        val questionsBox = switchBox("Context", { it.questions.enabled }) { info, on ->
            info.copy(questions = info.questions.copy(enabled = on))
        }

        val micMeter = HBox(METER_SPACING, micLabel, micBar)
        val ramMeter = HBox(METER_SPACING, ramLabel, ramBar, ramValueLabel)
        listOf(micMeter, ramMeter).forEach {
            it.alignment = Pos.CENTER_LEFT
            it.isMouseTransparent = true
        }

        // The dialog's buttons act on its two screens, Messages and Context; on Settings Start dialog stays in view, disabled.
        messagesTab.headerControls.disableProperty()
            .bind(tabPane.selectionModel.selectedIndexProperty().greaterThan(QUESTIONS_TAB_INDEX))

        // The top bar sits in the tab header row, right of the tab names: the dialog's buttons, the two switches,
        // then the microphone and memory meters, the token count and the method in use.
        // Not mouse-transparent like the meters: its tooltip breaks the count down.
        val tokensLabel = barLabel()

        // The method the dialog uses now: method 1 until it fails, then the next one until the dialog restarts.
        // The one label that may be cut short, down to its number alone: its tooltip says it in full.
        val methodLabel = Label().apply { style = overlayLabelStyle }
        var methodFull = ""
        var methodShort = ""
        val measure = Text()
        fun widthOf(text: String): Double {
            measure.font = methodLabel.font
            measure.text = text
            return Math.ceil(measure.layoutBounds.width) + 2.0
        }

        val topBar = HBox(BAR_SPACING, messagesTab.headerControls, translationBox, questionsBox, micMeter, ramMeter, tokensLabel, methodLabel).apply {
            alignment = Pos.CENTER_LEFT
            minHeight = TOP_BAR_HEIGHT
            prefHeight = TOP_BAR_HEIGHT
            maxHeight = TOP_BAR_HEIGHT
            maxWidth = Region.USE_PREF_SIZE
            StackPane.setAlignment(this, Pos.TOP_LEFT)
        }

        val root = StackPane(tabPane, topBar)

        // The tab names' width: the top bar starts where they end (set once the window is shown).
        var tabNamesWidth = 0.0

        val scaledRoot = ScaledPane(root).apply { scale = appInfo.uiScalePercent / 100.0 }

        // A window too narrow for the whole bar loses, in this order, the memory figures, the memory meter, the
        // token count and the microphone meter, and then the method's name, leaving its number. The buttons, the
        // switches and that number always stay: a window too narrow even for them is drawn smaller.
        val fitTopBar = {
            val giveWay = listOf(
                ramValueLabel to ramValueLabel.prefWidth(-1.0) + METER_SPACING,
                ramMeter to ramLabel.prefWidth(-1.0) + METER_SPACING + ramBar.prefWidth(-1.0) + BAR_SPACING,
                tokensLabel to tokensLabel.prefWidth(-1.0) + BAR_SPACING,
                micMeter to micMeter.prefWidth(-1.0) + BAR_SPACING,
            )
            val beforeMethod = messagesTab.headerControls.prefWidth(-1.0) + translationBox.prefWidth(-1.0) +
                questionsBox.prefWidth(-1.0) + 3 * BAR_SPACING
            val withMethodName = beforeMethod + widthOf(methodFull).coerceAtMost(METHOD_MIN_WIDTH)
            val available = root.width - tabNamesWidth - BAR_RIGHT_GAP
            val hidden = hiddenCount(available, withMethodName, giveWay.map { it.second })
            giveWay.forEachIndexed { place, (node, _) ->
                node.isVisible = place >= hidden
                node.isManaged = node.isVisible
            }
            methodLabel.text = if (withMethodName > available) methodShort else methodFull
            scaledRoot.minContentWidth = tabNamesWidth + beforeMethod + widthOf(methodShort) + BAR_RIGHT_GAP
        }
        root.widthProperty().addListener { _, _, _ -> fitTopBar() }
        // The buttons change with the screen, and with them the room left for the rest of the bar.
        val showButtons = { index: Int ->
            messagesTab.showButtonsFor(messagesScreen = index == MESSAGES_TAB_INDEX, contextScreen = index == QUESTIONS_TAB_INDEX)
            javafx.application.Platform.runLater(fitTopBar)
        }
        showButtons(tabPane.selectionModel.selectedIndex)
        tabPane.selectionModel.selectedIndexProperty().addListener { _, _, index -> showButtons(index.toInt()) }
        // Once the label has taken its new text in: its width is still the old one while the text changes.
        listOf(ramValueLabel, tokensLabel).forEach {
            it.textProperty().addListener { _, _, _ -> javafx.application.Platform.runLater(fitTopBar) }
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
                tokensLabel.text = tokensText(totals)
                tokensLabel.tooltip = Tooltip(tokensTooltip(totals))
            }
        }

        mainThreadScope.launch {
            combine(preferencesRepository.appInfoFlow, activeMethod.flow) { info, state -> info.translationMethods to state }
                .collect { (chain, state) ->
                    val index = state.index.coerceIn(0, chain.lastIndex)
                    val method = chain[index]
                    methodShort = "Method ${index + 1}"
                    methodFull = "$methodShort: ${method.shortTitle}"
                    fitTopBar()
                    methodLabel.style = overlayLabelStyle + if (index > 0) " -fx-text-fill: $RESERVE_COLOUR;" else ""
                    methodLabel.tooltip = Tooltip(
                        buildString {
                            appendLine("In use: ${method.title}")
                            state.left.forEach { (key, why) ->
                                appendLine("Left ${chain.firstOrNull { it.key == key }?.title ?: key}: $why")
                            }
                            append("Stop and start the dialog to begin with method 1 again.")
                        }
                    )
                }
        }

        mainThreadScope.launch {
            audioChunker.noiceLevelFlow.collect { level ->
                micBar.progress = level.coerceIn(0, 100) / 100.0
            }
        }

        // Settings → General: the whole window drawn larger or smaller.
        mainThreadScope.launch {
            preferencesRepository.appInfoFlow.map { it.uiScalePercent }.distinctUntilChanged()
                .collect { scaledRoot.scale = it / 100.0 }
        }

        scalePopupsLike(scaledRoot)

        val screenBounds: Rectangle2D =
            Screen.getScreens().let { if (it.size > 1) it[1] else it[0] }.visualBounds

        val stylesheetUrl = DialogApplication::class.java.getResource("/styles/styles.css")?.toExternalForm()
        if (stylesheetUrl == null) {
            System.err.println("[DialogApplication] /styles/styles.css not found on classpath")
        }

        val scene = Scene(scaledRoot, screenBounds.width, screenBounds.height).apply {
            stylesheetUrl?.let { stylesheets.add(it) }
        }

        primaryStage.apply {
            x = screenBounds.minX
            y = screenBounds.minY
            width = screenBounds.width
            height = screenBounds.height
            title = "Dialog Application"
            icons.addAll(listOf(16, 24, 32, 48, 64, 128, 256).map { Image("/icons/app-$it.png") })
            this.scene = scene
            show()
        }
        windowPresence.attach(primaryStage)

        javafx.application.Platform.runLater {
            val headersRegion = tabPane.lookup(".tab-header-area > .headers-region") as? Region
            if (headersRegion != null) {
                val placeTopBar = {
                    tabNamesWidth = headersRegion.width
                    StackPane.setMargin(topBar, Insets(0.0, BAR_RIGHT_GAP, 0.0, tabNamesWidth))
                    fitTopBar()
                }
                headersRegion.widthProperty().addListener { _, _, _ -> placeTopBar() }
                placeTopBar()
            } else {
                System.err.println("[DialogApplication] .headers-region not found, top bar pinned to left edge")
            }
        }
    }

}
