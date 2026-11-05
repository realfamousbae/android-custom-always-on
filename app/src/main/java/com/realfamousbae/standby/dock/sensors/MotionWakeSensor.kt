package com.realfamousbae.standby.dock.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Wake-up датчик «наклона» (Tilt Detector, тип 22): срабатывает, когда телефон заметно
 * повернули, и будит процессор даже при погашенном экране. Нужен, чтобы без зарядки
 * заметить «поставили погашенный телефон на подставку», не держа акселерометр включённым.
 *
 * Константа типа скрыта в SDK, поэтому ищем датчик по номеру в общем списке. На
 * устройствах без такого датчика Flow пустой, и работает только сценарий «экран погас».
 */
class MotionWakeSensor(private val sensorManager: SensorManager) {

    private val sensor: Sensor? = sensorManager.getSensorList(Sensor.TYPE_ALL)
        .firstOrNull { it.type == TYPE_TILT_DETECTOR && it.isWakeUpSensor }

    val description: String get() = sensor?.let { "${it.name} (${it.vendor})" } ?: "нет"

    fun events(): Flow<Unit> {
        val s = sensor ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(Unit)
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }
            val ok = sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
            if (!ok) close(IllegalStateException("Не удалось подписаться на ${s.name}"))
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }

    private companion object {
        /** `Sensor.TYPE_TILT_DETECTOR` (@hide). */
        const val TYPE_TILT_DETECTOR = 22
    }
}
