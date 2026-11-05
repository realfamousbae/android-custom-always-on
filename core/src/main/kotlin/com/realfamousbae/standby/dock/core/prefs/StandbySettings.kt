package com.realfamousbae.standby.dock.core.prefs

/**
 * Пользовательские настройки, от которых зависит логика решений. Хранятся в DataStore
 * (слой :app), здесь — только модель и значения по умолчанию.
 */
data class StandbySettings(
    /** Допустимое отклонение длинной грани от вертикали, при котором телефон «на боку». */
    val maxTiltDegrees: Float = 45f,
    /** Показывать режим только на зарядке. По умолчанию работает и без неё. */
    val requireCharging: Boolean = false,
    /** Без зарядки режим не включается и закрывается, если заряд ниже порога. */
    val minBatteryPercentOffCharger: Int = 20,
    /** Пределы автояркости окна режима (доля от максимальной, 0..1). */
    val brightnessMin: Float = 0.01f,
    val brightnessMax: Float = 0.6f,
) {
    companion object {
        const val TILT_RANGE_MIN = 15f
        const val TILT_RANGE_MAX = 75f
        const val BATTERY_RANGE_MIN = 5
        const val BATTERY_RANGE_MAX = 80
    }
}
