package com.realfamousbae.standby.dock.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.realfamousbae.standby.dock.core.trigger.Gravity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Адаптер акселерометра в Flow<Gravity>. Подписка на датчик живёт ровно столько,
 * сколько коллектор Flow, — так сервис не может забыть отписаться.
 *
 * Предпочитаем wake-up вариант: он будит процессор, поэтому события приходят и при
 * погашенном экране. Если его нет, вызывающий код должен держать PARTIAL_WAKE_LOCK.
 */
class GravitySensor(private val sensorManager: SensorManager) {

    private val wakeUpSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
    private val sensor: Sensor? =
        wakeUpSensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = sensor != null
    val isWakeUp: Boolean get() = wakeUpSensor != null
    val description: String
        get() = sensor?.let { "${it.name} (${it.vendor}), wakeUp=${it.isWakeUpSensor}" } ?: "нет"

    fun gravity(): Flow<Gravity> = callbackFlow {
        val s = sensor ?: run {
            close(IllegalStateException("На устройстве нет акселерометра"))
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Gravity(event.values[0], event.values[1], event.values[2]))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        // ~5 Гц, допускаем пакетную доставку с задержкой до 1 с: экономит пробуждения.
        sensorManager.registerListener(
            listener, s, SensorManager.SENSOR_DELAY_NORMAL, MAX_REPORT_LATENCY_US,
        )
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        const val MAX_REPORT_LATENCY_US = 1_000_000
    }
}
