package utils

import app.DialogApplication
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfoNullable
import models.domain.FloatingPanelMode
import models.domain.HotkeyAction
import ui.SettingsTab
import models.domain.WindowSettings
import models.domain.WindowSettingsNullable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowSettingsTest {

    private class NoEnv : EnvSettings {
        override fun getString(key: String): String? = null
        override fun getInt(key: String, min: Int): Int? = null
        override fun getLong(key: String, min: Long): Long? = null
        override fun getBoolean(key: String): Boolean? = null
        override fun <T : Enum<T>> getEnum(key: String, values: Array<T>): T? = null
        override fun noteOverriddenByStoredValue(key: String) {}
    }

    private val mapper = ApplicationInfoMapper()
    private val overlay = ApplicationInfoEnvOverlay(NoEnv())

    @Test
    fun `translation is on and the window at full size until the user says otherwise`() {
        val info = mapper.reverse(ApplicationInfoNullable())
        assertTrue(info.translationEnabled)
        assertEquals(100, info.uiScalePercent)
    }

    @Test
    fun `a saved switch and size are read back, a size the screen does not offer is not`() {
        val saved = mapper.reverse(ApplicationInfoNullable(translationEnabled = false, uiScalePercent = 70))
        assertFalse(saved.translationEnabled)
        assertEquals(70, saved.uiScalePercent)
        assertEquals(100, mapper.reverse(ApplicationInfoNullable(uiScalePercent = 400)).uiScalePercent)
        assertEquals(100, mapper.reverse(ApplicationInfoNullable(uiScalePercent = 75)).uiScalePercent)
    }

    @Test
    fun `only a changed switch or size is stored`() {
        val untouched = overlay.dropFallbackValues(mapper.map(mapper.reverse(ApplicationInfoNullable())))
        assertNull(untouched.translationEnabled)
        assertNull(untouched.uiScalePercent)

        val changed = mapper.reverse(ApplicationInfoNullable()).copy(translationEnabled = false, uiScalePercent = 150)
        val stored = overlay.dropFallbackValues(mapper.map(changed))
        assertEquals(false, stored.translationEnabled)
        assertEquals(150, stored.uiScalePercent)
    }

    @Test
    fun `a narrow window drops the top bar's optional parts in order, never more than it must`() {
        val giveWay = listOf(100.0, 200.0, 150.0, 170.0)
        assertEquals(0, DialogApplication.hiddenCount(available = 1620.0, alwaysShown = 1000.0, giveWay = giveWay), "everything fits exactly")
        assertEquals(1, DialogApplication.hiddenCount(available = 1619.0, alwaysShown = 1000.0, giveWay = giveWay))
        assertEquals(1, DialogApplication.hiddenCount(available = 1520.0, alwaysShown = 1000.0, giveWay = giveWay))
        assertEquals(2, DialogApplication.hiddenCount(available = 1519.0, alwaysShown = 1000.0, giveWay = giveWay))
        assertEquals(3, DialogApplication.hiddenCount(available = 1200.0, alwaysShown = 1000.0, giveWay = giveWay))
        assertEquals(4, DialogApplication.hiddenCount(available = 900.0, alwaysShown = 1000.0, giveWay = giveWay), "nothing optional is left to drop")
    }

    @Test
    fun `the desktop settings start as they were, and only the changed ones are stored`() {
        val untouched = mapper.reverse(ApplicationInfoNullable())
        assertEquals(WindowSettings(), untouched.window)
        assertNull(overlay.dropFallbackValues(mapper.map(untouched)).window)

        val changed = untouched.copy(
            window = WindowSettings(
                closeToTray = true,
                hotkeys = WindowSettings().hotkeys + mapOf(HotkeyAction.WINDOW to "", HotkeyAction.CLEAR_ANSWERS to "Ctrl+Alt+X"),
                floatingPanel = FloatingPanelMode.WHEN_HIDDEN,
                panelOpacityPercent = 60,
            ),
        )
        val stored = overlay.dropFallbackValues(mapper.map(changed)).window
        assertEquals(
            WindowSettingsNullable(
                closeToTray = true,
                hotkeys = mapOf("WINDOW" to "", "CLEAR_ANSWERS" to "Ctrl+Alt+X"),
                floatingPanel = "WHEN_HIDDEN",
                panelOpacityPercent = 60,
            ),
            stored,
        )
        // No hotkey (the window's here) is a choice that survives the round trip, not an emptied field that falls back to the default.
        assertEquals(changed.window, mapper.reverse(ApplicationInfoNullable(window = stored)).window)
        val odd = mapper.reverse(
            ApplicationInfoNullable(
                window = WindowSettingsNullable(floatingPanel = "SOMETIMES", panelOpacityPercent = 5, hotkeys = mapOf("GONE" to "Ctrl+G")),
            ),
        )
        assertEquals(WindowSettings(), odd.window)
    }

    @Test
    fun `a hotkey is a modifier with one key, written the same way whatever the input`() {
        assertEquals(Hotkey(Hotkey.MOD_CONTROL or Hotkey.MOD_ALT, 'H'.code, "Ctrl+Alt+H"), Hotkey.parse("alt + ctrl + h"))
        assertEquals(Hotkey(Hotkey.MOD_SHIFT or Hotkey.MOD_WIN, 0x74, "Shift+Win+F5"), Hotkey.parse("Win+Shift+F5"))
        assertEquals("Ctrl+Space", Hotkey.parse("control+space")?.text)
        assertEquals(0x7B + 12, Hotkey.parse("Ctrl+F24")?.keyCode)
        listOf("", "H", "Ctrl", "Ctrl+H+J", "Ctrl+F25", "Ctrl+Enter", "Ctrl++").forEach { assertNull(Hotkey.parse(it), it) }
    }

    @Test
    fun `hotkeys are saved only when each is a combination and none is given twice`() {
        val defaults = HotkeyAction.entries.associateWith { it.defaultKeys }
        assertNull(SettingsTab.hotkeysProblem(defaults))
        assertNull(SettingsTab.hotkeysProblem(defaults + (HotkeyAction.DIALOG to "")))
        assertEquals(
            "Ctrl+Alt+H is given to both Hide and show the window and Answer now",
            SettingsTab.hotkeysProblem(defaults + (HotkeyAction.ANSWER_NOW to "alt+ctrl+h")),
        )
        assertTrue(SettingsTab.hotkeysProblem(defaults + (HotkeyAction.PANEL to "P"))!!.startsWith("Hide and show the floating panel:"))
    }
}
