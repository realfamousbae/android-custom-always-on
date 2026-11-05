package com.realfamousbae.standby.dock.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "standby_settings")

/**
 * Хранение [StandbySettings] в DataStore. Один экземпляр на процесс (живёт в AppGraph):
 * DataStore запрещает два экземпляра на один файл.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore
    private val defaults = StandbySettings()

    val settings: Flow<StandbySettings> = store.data
        .map { p ->
            StandbySettings(
                maxTiltDegrees = p[MAX_TILT] ?: defaults.maxTiltDegrees,
                requireCharging = p[REQUIRE_CHARGING] ?: defaults.requireCharging,
                minBatteryPercentOffCharger = p[MIN_BATTERY] ?: defaults.minBatteryPercentOffCharger,
                brightnessMin = p[BRIGHTNESS_MIN] ?: defaults.brightnessMin,
                brightnessMax = p[BRIGHTNESS_MAX] ?: defaults.brightnessMax,
            )
        }
        .distinctUntilChanged()

    suspend fun setMaxTilt(value: Float) = set(MAX_TILT, value)
    suspend fun setRequireCharging(value: Boolean) = set(REQUIRE_CHARGING, value)
    suspend fun setMinBattery(value: Int) = set(MIN_BATTERY, value)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val MAX_TILT = floatPreferencesKey("max_tilt_degrees")
        val REQUIRE_CHARGING = booleanPreferencesKey("require_charging")
        val MIN_BATTERY = intPreferencesKey("min_battery_percent_off_charger")
        val BRIGHTNESS_MIN = floatPreferencesKey("brightness_min")
        val BRIGHTNESS_MAX = floatPreferencesKey("brightness_max")
    }
}
