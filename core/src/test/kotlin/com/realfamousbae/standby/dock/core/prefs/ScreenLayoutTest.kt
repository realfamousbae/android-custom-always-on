package com.realfamousbae.standby.dock.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenLayoutTest {

    @Test
    fun `без виджета остаются только часы`() {
        assertEquals(ScreenLayout.ClockOnly, ScreenLayout.resolve(ScreenLayout.ClockAndWidget, widgetAvailable = false))
        assertEquals(ScreenLayout.ClockAndWidget, ScreenLayout.resolve(ScreenLayout.ClockAndWidget, widgetAvailable = true))
        assertEquals(ScreenLayout.ClockOnly, ScreenLayout.resolve(ScreenLayout.ClockOnly, widgetAvailable = true))
    }

    @Test
    fun `разбор сохранённого значения`() {
        assertEquals(ScreenLayout.ClockAndWidget, ScreenLayout.parse("ClockAndWidget"))
        assertEquals(ScreenLayout.ClockOnly, ScreenLayout.parse("мусор"))
        assertEquals(ScreenLayout.ClockOnly, ScreenLayout.parse(null))
    }
}
