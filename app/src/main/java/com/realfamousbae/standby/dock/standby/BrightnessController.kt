package com.realfamousbae.standby.dock.standby

import android.os.SystemClock
import android.view.Window
import com.realfamousbae.standby.dock.core.brightness.AutoBrightness
import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import com.realfamousbae.standby.dock.sensors.LightSensor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Применяет автояркость к окну режима. Логика плавности — в [AutoBrightness] (:core),
 * здесь только датчик, таймер и запись в `window.attributes`.
 */
class BrightnessController(
    private val window: Window,
    private val lightSensor: LightSensor,
    private val settings: StateFlow<StandbySettings>,
) {
    private val auto = AutoBrightness()

    /** Работает, пока жива корутина (Activity запускает её на время STARTED). */
    suspend fun run() = coroutineScope {
        if (!lightSensor.isAvailable) {
            apply(settings.value.brightnessMin)
            return@coroutineScope
        }
        var lastLux: Float? = null
        launch { lightSensor.lux().collect { lastLux = it } }
        // Датчик молчит, пока свет не меняется, — поэтому подкручиваем по таймеру.
        while (true) {
            lastLux?.let { lux ->
                val s = settings.value
                auto.update(lux, SystemClock.elapsedRealtime(), s.brightnessMin, s.brightnessMax)
                    ?.let(::apply)
            }
            delay(TICK_MS)
        }
    }

    private fun apply(brightness: Float) {
        window.attributes = window.attributes.apply { screenBrightness = brightness }
    }

    private companion object {
        const val TICK_MS = 200L
    }
}
