package com.realfamousbae.standby.dock.core.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationClassifierTest {

    private val g = 9.81f

    /** Телефон на боку, откинут назад на [deg] градусов (как на подставке). */
    private fun onStandTilted(deg: Double) = Gravity(
        x = (g * cos(Math.toRadians(deg))).toFloat(),
        y = 0f,
        z = (g * sin(Math.toRadians(deg))).toFloat(),
    )

    @Test
    fun `строго вертикально на боку — отклонение 0`() {
        assertEquals(0f, OrientationClassifier.tiltFromVerticalDegrees(Gravity(g, 0f, 0f))!!, 0.01f)
        assertEquals(0f, OrientationClassifier.tiltFromVerticalDegrees(Gravity(-g, 0f, 0f))!!, 0.01f)
    }

    @Test
    fun `подставка с наклоном 30 градусов считается стоящим на боку`() {
        assertTrue(OrientationClassifier.isOnSide(onStandTilted(30.0), maxTiltDegrees = 45f))
    }

    @Test
    fun `наклон больше порога — не на боку`() {
        assertFalse(OrientationClassifier.isOnSide(onStandTilted(50.0), maxTiltDegrees = 45f))
    }

    @Test
    fun `плашмя экраном вверх — не на боку`() {
        assertFalse(OrientationClassifier.isOnSide(Gravity(0f, 0f, g), maxTiltDegrees = 45f))
    }

    @Test
    fun `в портрете — не на боку`() {
        assertFalse(OrientationClassifier.isOnSide(Gravity(0f, g, 0f), maxTiltDegrees = 45f))
    }

    @Test
    fun `слишком маленький модуль — измерение непригодно`() {
        assertNull(OrientationClassifier.tiltFromVerticalDegrees(Gravity(0.5f, 0.5f, 0.5f)))
        assertFalse(OrientationClassifier.isOnSide(Gravity(0.5f, 0f, 0f), maxTiltDegrees = 45f))
    }
}
