package com.realfamousbae.standby.dock.core.trigger

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Вектор силы тяжести в системе координат устройства (м/с²), как его отдаёт акселерометр.
 * Ось X — вдоль короткой стороны экрана, Y — вдоль длинной, Z — из экрана к пользователю.
 */
data class Gravity(val x: Float, val y: Float, val z: Float) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

/**
 * Мгновенная (без дебаунса) классификация положения телефона.
 *
 * Телефон «стоит на боку», когда ось X почти совпадает с вертикалью: тогда он опирается
 * на длинную грань. Отклонение от вертикали считается как угол между осью X и вектором
 * тяжести. Плашмя (gx ≈ 0) и в портрете (gy ≈ 9.8) отклонение близко к 90°, поэтому
 * отдельные проверки «не плашмя» не нужны. Дебаунс и гистерезис — забота TriggerEngine.
 */
object OrientationClassifier {

    /** Модуль ниже этого значения — датчик отдал мусор или устройство в свободном падении. */
    private const val MIN_VALID_MAGNITUDE = 3f

    /**
     * Угол отклонения оси X от вертикали в градусах (0..90), либо null, если измерение
     * непригодно (например, телефон трясут или падает).
     */
    fun tiltFromVerticalDegrees(g: Gravity): Float? {
        val m = g.magnitude
        if (m < MIN_VALID_MAGNITUDE) return null
        val cos = (abs(g.x) / m).coerceIn(0f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /** Стоит ли телефон на боку при заданном допустимом отклонении от вертикали. */
    fun isOnSide(g: Gravity, maxTiltDegrees: Float): Boolean {
        val tilt = tiltFromVerticalDegrees(g) ?: return false
        return tilt < maxTiltDegrees
    }
}
