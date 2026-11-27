package models.domain

/** Settings → System: how the window shows itself on the desktop, and how it gets out of the way. */
data class WindowSettings(
    /** The minimise button hides the window to its icon in the notification area, instead of the taskbar. */
    val minimizeToTray: Boolean = false,
    /** The close button hides the window to its icon in the notification area; Exit in the icon's menu quits. */
    val closeToTray: Boolean = false,
    /** No taskbar button and no Alt+Tab entry, even while the window is shown; minimising then hides it. */
    val hideFromTaskbar: Boolean = false,
    /**
     * The key combination of each action, working from any program, as [utils.Hotkey.parse] reads it; blank: none.
     * Every action has an entry.
     */
    val hotkeys: Map<HotkeyAction, String> = HotkeyAction.entries.associateWith { it.defaultKeys },
    /** A small window on top of the others with the latest question and its answer. */
    val floatingPanel: FloatingPanelMode = FloatingPanelMode.OFF,
    /** One of [PANEL_OPACITY_PERCENTS]. */
    val panelOpacityPercent: Int = 90,
    /** The notification-area icon is marked when an answer arrives while the window is hidden. */
    val newAnswerSignal: Boolean = true,
) {
    companion object {
        val PANEL_OPACITY_PERCENTS: List<Int> = (30..100 step 10).toList()
    }
}

/** What a global hotkey does (Settings → System), with the combination it has until the user changes it. */
enum class HotkeyAction(val title: String, val defaultKeys: String) {
    WINDOW("Hide and show the window", "Ctrl+Alt+H"),
    DIALOG("Start and stop the dialog", "Ctrl+Alt+S"),
    ANSWER_NOW("Answer now", "Ctrl+Alt+A"),
    CLEAR_ANSWERS("Clear answers", ""),
    PANEL("Hide and show the floating panel", "Ctrl+Alt+P"),
    COPY_ANSWER("Copy the latest answer", "Ctrl+Alt+C"),
}

enum class FloatingPanelMode(val title: String) {
    OFF("Off"),
    WHEN_HIDDEN("While the window is hidden"),
    ALWAYS("Always"),
}

/** Only the values the user changed; the panel mode by its name. */
data class WindowSettingsNullable(
    val minimizeToTray: Boolean? = null,
    val closeToTray: Boolean? = null,
    val hideFromTaskbar: Boolean? = null,
    /** Only the actions whose combination the user changed, by the action's name. */
    val hotkeys: Map<String, String>? = null,
    val floatingPanel: String? = null,
    val panelOpacityPercent: Int? = null,
    val newAnswerSignal: Boolean? = null,
)
