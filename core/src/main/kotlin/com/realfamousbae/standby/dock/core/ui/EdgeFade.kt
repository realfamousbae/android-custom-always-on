package com.realfamousbae.standby.dock.core.ui

import kotlin.math.abs
import kotlin.math.exp

/**
 * Профиль затухания у края карточки: непрозрачность «вуали» цвета карточки поверх виджета.
 * Форма — интеграл гауссианы (как край, размытый по Гауссу): без изломов на концах, поэтому
 * глаз не видит ни начала, ни конца перехода. Линейный градиент даёт видимые полосы
 * в точках излома.
 */
object EdgeFade {

    /** Число стопов градиента: при 16 кривая неотличима от непрерывной. */
    const val STOPS = 16

    /** Крутизна: на границах профиль отличается от 0/1 меньше чем на 1/255 после нормировки. */
    private const val SHARPNESS = 5.0

    /**
     * @param t положение внутри полосы: 0 — у самого края карточки, 1 — внутренняя граница.
     * @return непрозрачность вуали: 1 у края, 0 на внутренней границе.
     */
    fun veilAlpha(t: Float): Float {
        val x = t.coerceIn(0f, 1f).toDouble()
        val raw = { v: Double -> 1.0 - gaussCdf((v - 0.5) * SHARPNESS) }
        val lo = raw(1.0)
        val hi = raw(0.0)
        return ((raw(x) - lo) / (hi - lo)).toFloat()
    }

    /** Позиции стопов (0..1) и непрозрачность в них. */
    fun stops(): List<Pair<Float, Float>> =
        (0 until STOPS).map { i -> val t = i / (STOPS - 1f); t to veilAlpha(t) }

    private fun gaussCdf(z: Double): Double = 0.5 * (1.0 + erf(z / Math.sqrt(2.0)))

    /** Приближение Абрамовица — Стиган 7.1.26, погрешность < 1.5e-7. */
    private fun erf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.3275911 * abs(x))
        val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
        val y = 1.0 - poly * exp(-x * x)
        return if (x >= 0) y else -y
    }
}
