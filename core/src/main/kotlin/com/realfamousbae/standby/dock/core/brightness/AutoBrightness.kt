package com.realfamousbae.standby.dock.core.brightness

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Кривая «освещённость → яркость окна». Глаз воспринимает свет логарифмически, поэтому
 * яркость растёт линейно по log10(люкс): 1 лк и темнее → [min], [LUX_FULL] и светлее → [max].
 */
object BrightnessCurve {
    const val LUX_DARK = 1f
    const val LUX_FULL = 1_000f

    fun map(lux: Float, min: Float, max: Float): Float {
        if (lux <= LUX_DARK) return min
        if (lux >= LUX_FULL) return max
        val t = log10(lux / LUX_DARK) / log10(LUX_FULL / LUX_DARK)
        return min + (max - min) * t
    }
}

/**
 * Автояркость с плавностью: показания датчика сглаживаются экспоненциально (в
 * логарифмической шкале, чтобы вспышка фар за окном не дёргала экран), результат
 * меняется не быстрее [maxStepPerSecond] и не чаще, чем выходит за мёртвую зону.
 *
 * Датчик света на Android отдаёт события только при изменении, поэтому вызывающий код
 * должен вызывать [update] по таймеру с последним известным значением — иначе сглаживание
 * «застрянет» на полпути.
 */
class AutoBrightness(
    private val timeConstantMs: Long = 3_000,
    private val maxStepPerSecond: Float = 0.25f,
    private val deadBand: Float = 0.005f,
) {
    private var smoothedLogLux: Float? = null
    private var output: Float? = null
    private var lastUpdateMs: Long = 0

    /**
     * @return новая яркость, если её стоит применить к окну, иначе null.
     */
    fun update(lux: Float, nowMs: Long, minBrightness: Float, maxBrightness: Float): Float? {
        val logLux = log10(max(lux, 0f) + 1f)
        val prevLog = smoothedLogLux
        val prevOut = output
        if (prevLog == null || prevOut == null) {
            // Первое показание — сразу в нужную яркость, без анимации.
            smoothedLogLux = logLux
            lastUpdateMs = nowMs
            return BrightnessCurve.map(lux, minBrightness, maxBrightness).also { output = it }
        }

        val dt = (nowMs - lastUpdateMs).coerceAtLeast(0)
        lastUpdateMs = nowMs
        val alpha = 1f - exp(-dt.toFloat() / timeConstantMs)
        val smoothed = prevLog + (logLux - prevLog) * alpha
        smoothedLogLux = smoothed

        val smoothedLux = Math.pow(10.0, smoothed.toDouble()).toFloat() - 1f
        val target = BrightnessCurve.map(smoothedLux, minBrightness, maxBrightness)
        val maxStep = maxStepPerSecond * dt / 1_000f
        val next = prevOut + (target - prevOut).coerceIn(-maxStep, maxStep)
        // Границы могли поменяться в настройках — держим результат в них.
        val clamped = min(max(next, minBrightness), maxBrightness)
        if (abs(clamped - prevOut) < deadBand && clamped != target) return null
        if (clamped == prevOut) return null
        output = clamped
        return clamped
    }
}
