package utils

import app.DialogApplication
import mappers.domain.ApplicationInfoMapper
import models.domain.ApplicationInfoNullable
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
}
