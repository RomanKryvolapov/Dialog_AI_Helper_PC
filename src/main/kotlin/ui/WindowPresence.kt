package ui

import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.FloatingPanelMode
import models.domain.WindowSettings
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import models.domain.HotkeyAction
import utils.GlobalHotkeys
import utils.QuestionsAssistant
import utils.Hotkey
import utils.WindowsDesktop
import kotlinx.coroutines.flow.StateFlow
import kotlin.system.exitProcess

/**
 * How the main window shows itself on the desktop and gets out of the way (Settings → System): the icon in the
 * notification area, minimising and closing to it, the global hotkeys, staying off the taskbar and Alt+Tab, the
 * floating panel and the new-answer mark. "Hidden" means not shown or minimised; touched on the FX thread only.
 */
class WindowPresence(
    private val preferences: PreferencesRepository,
    private val messagesTab: MessagesTab,
    private val questionsTab: QuestionsTab,
    private val questionsAssistant: QuestionsAssistant,
) {

    private val log = LoggerFactory.getLogger("WindowPresence")

    private lateinit var stage: Stage
    private var tray: AppTray? = null
    private val hotkeys = GlobalHotkeys<HotkeyAction> { action -> Platform.runLater { onHotkey(action) } }
    private val panel = FloatingPanel { showWindow() }
    private var settings: WindowSettings = preferences.getAppInfo().window
    /** An answer came while the window was hidden, and it has not been shown since. */
    private var unseenAnswer = false
    private var lastSerial = 0L
    /** Set by the panel's hotkey: shown or hidden whatever its setting says, until that setting changes. */
    private var panelOverride: Boolean? = null

    /** The hotkeys that could not be registered, and why: for Settings → System. */
    val hotkeyProblems: StateFlow<Map<HotkeyAction, String>> get() = hotkeys.problems

    /** Takes over [stage] once it is shown. */
    fun attach(stage: Stage) {
        this.stage = stage
        // Hiding the window to the tray must not end the app, as hiding the last window otherwise does.
        Platform.setImplicitExit(false)
        tray = AppTray.create(
            onToggleWindow = { Platform.runLater { toggleWindow() } },
            onToggleDialog = { Platform.runLater { messagesTab.toggleDialog() } },
            onExit = { Platform.runLater { exit() } },
        )
        stage.setOnCloseRequest { event ->
            if (settings.closeToTray && tray != null) {
                event.consume()
                stage.hide()
            } else {
                exit()
            }
        }
        stage.iconifiedProperty().addListener { _, _, iconified ->
            // Owned by the hidden window, a minimised window would sink to a stub above the taskbar: it is hidden.
            if (iconified && ((settings.minimizeToTray && tray != null) || settings.hideFromTaskbar)) {
                stage.hide()
                stage.isIconified = false
            }
            refresh()
        }
        stage.showingProperty().addListener { _, _, showing ->
            if (showing) applyTaskbar()
            refresh()
        }
        apply(settings, first = true)
        mainThreadScope.launch {
            preferences.appInfoFlow.map { it.window }.distinctUntilChanged().collect { apply(it, first = false) }
        }
        mainThreadScope.launch { messagesTab.dialogRunning.collect { refresh() } }
        mainThreadScope.launch {
            questionsTab.latest.collect { latest ->
                panel.show(latest)
                if (latest != null && latest.serial != lastSerial && isHidden() && settings.newAnswerSignal) unseenAnswer = true
                lastSerial = latest?.serial ?: 0L
                refresh()
            }
        }
    }

    private fun apply(new: WindowSettings, first: Boolean) {
        val old = settings
        settings = new
        if (first || new.hotkeys != old.hotkeys) {
            hotkeys.register(
                new.hotkeys.filterValues { it.isNotBlank() }.mapNotNull { (action, keys) ->
                    val parsed = Hotkey.parse(keys)
                    if (parsed == null) log.warn("Not a hotkey this app can register: $keys")
                    parsed?.let { action to it }
                }.toMap(),
            )
        }
        if (new.floatingPanel != old.floatingPanel) panelOverride = null
        if (first || new.hideFromTaskbar != old.hideFromTaskbar) applyTaskbar()
        if (!new.newAnswerSignal) unseenAnswer = false
        panel.setOpacity(new.panelOpacityPercent)
        refresh()
    }

    private fun applyTaskbar() {
        if (!stage.isShowing) return
        WindowsDesktop.findWindow(stage.title)?.let { WindowsDesktop.setOffTaskbar(it, settings.hideFromTaskbar) }
    }

    private fun isHidden(): Boolean = !stage.isShowing || stage.isIconified

    private fun onHotkey(action: HotkeyAction) {
        when (action) {
            // Shows a hidden window or one behind others; hides the one in front.
            HotkeyAction.WINDOW -> if (isHidden() || !stage.isFocused) showWindow() else stage.hide()
            HotkeyAction.DIALOG -> messagesTab.toggleDialog()
            // As the button: only while a dialog is listened to.
            HotkeyAction.ANSWER_NOW -> if (messagesTab.dialogRunning.value) questionsAssistant.answerNow()
            HotkeyAction.CLEAR_ANSWERS -> questionsAssistant.clear()
            HotkeyAction.PANEL -> {
                panelOverride = !panelWanted()
                refresh()
            }
            HotkeyAction.COPY_ANSWER -> questionsTab.latest.value?.let { latest ->
                Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(latest.answer) })
            }
        }
    }

    private fun panelWanted(): Boolean = panelOverride ?: when (settings.floatingPanel) {
        FloatingPanelMode.OFF -> false
        FloatingPanelMode.WHEN_HIDDEN -> isHidden()
        FloatingPanelMode.ALWAYS -> true
    }

    private fun toggleWindow() {
        if (isHidden()) showWindow() else stage.hide()
    }

    private fun showWindow() {
        stage.show()
        stage.isIconified = false
        stage.toFront()
        stage.requestFocus()
    }

    /** The tray icon and the panel follow the window, the dialog and the settings. */
    private fun refresh() {
        val hidden = isHidden()
        if (!hidden) unseenAnswer = false
        tray?.show(
            AppTray.State(
                windowShown = !hidden,
                listening = messagesTab.dialogRunning.value,
                unseenAnswer = unseenAnswer,
            ),
        )
        panel.setShown(panelWanted())
    }

    /** Quits: the hotkeys and the tray icon go first, or AWT's thread would keep the process alive. */
    fun exit() {
        hotkeys.stop()
        tray?.remove()
        Platform.exit()
        exitProcess(0)
    }
}
