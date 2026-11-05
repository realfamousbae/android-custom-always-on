package com.realfamousbae.standby.dock.core.brightness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBrightnessTest {

    private val min = 0.01f
    private val max = 0.6f

    @Test
    fun `кривая — темнота даёт минимум, яркий свет максимум, середина логарифмическая`() {
        assertEquals(min, BrightnessCurve.map(0f, min, max), 1e-6f)
        assertEquals(min, BrightnessCurve.map(1f, min, max), 1e-6f)
        assertEquals(max, BrightnessCurve.map(5_000f, min, max), 1e-6f)
        // 31.6 лк — ровно середина шкалы log10 от 1 до 1000.
        assertEquals((min + max) / 2, BrightnessCurve.map(31.62f, min, max), 0.002f)
    }

    @Test
    fun `первое показание применяется сразу`() {
        val auto = AutoBrightness()
        assertEquals(max, auto.update(2_000f, 0, min, max)!!, 1e-6f)
    }

    @Test
    fun `выключили свет — яркость падает плавно и доходит до минимума`() {
        val auto = AutoBrightness()
        auto.update(500f, 0, min, max)
        val first = auto.update(0f, 200, min, max)!!
        assertTrue("за 200 мс не больше 0.05", first > 0.45f)

        var last = first
        var t = 200L
        while (t < 30_000) {
            t += 200
            auto.update(0f, t, min, max)?.let {
                assertTrue("яркость только убывает", it <= last)
                last = it
            }
        }
        assertEquals(min, last, 0.01f)
    }

    @Test
    fun `короткая вспышка света почти не меняет яркость`() {
        val auto = AutoBrightness()
        auto.update(0f, 0, min, max)
        val afterFlash = auto.update(2_000f, 300, min, max) ?: min
        assertTrue("вспышка 300 мс — меньше 0.1", afterFlash < 0.1f)
    }

    @Test
    fun `стабильный свет не даёт лишних обновлений`() {
        val auto = AutoBrightness()
        auto.update(50f, 0, min, max)
        var t = 0L
        repeat(50) {
            t += 200
            assertNull(auto.update(50f, t, min, max))
        }
    }

    @Test
    fun `сузили максимум в настройках — значение возвращается в границы`() {
        val auto = AutoBrightness()
        auto.update(2_000f, 0, min, max)
        val next = auto.update(2_000f, 200, min, 0.3f)!!
        assertTrue(next <= 0.3f)
    }
}
