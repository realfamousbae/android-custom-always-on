package com.realfamousbae.standby.dock.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.realfamousbae.standby.dock.core.prefs.ScreenLayout
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
                layout = ScreenLayout.parse(p[LAYOUT]),
                widgetId = p[WIDGET_ID],
                notificationBannersEnabled = p[BANNERS_ENABLED] ?: defaults.notificationBannersEnabled,
                notificationBlockedPackages = p[BLOCKED_PACKAGES] ?: defaults.notificationBlockedPackages,
                hideSystemHeadsUp = p[HIDE_SYSTEM_HEADS_UP] ?: defaults.hideSystemHeadsUp,
            )
        }
        .distinctUntilChanged()

    suspend fun setMaxTilt(value: Float) = set(MAX_TILT, value)
    suspend fun setRequireCharging(value: Boolean) = set(REQUIRE_CHARGING, value)
    suspend fun setMinBattery(value: Int) = set(MIN_BATTERY, value)
    suspend fun setLayout(value: ScreenLayout) = set(LAYOUT, value.name)
    suspend fun setBannersEnabled(value: Boolean) = set(BANNERS_ENABLED, value)
    suspend fun setHideSystemHeadsUp(value: Boolean) = set(HIDE_SYSTEM_HEADS_UP, value)

    suspend fun setWidgetId(value: Int?) {
        store.edit { if (value == null) it.remove(WIDGET_ID) else it[WIDGET_ID] = value }
    }

    /** Разрешить или запретить баннеры от приложения. */
    suspend fun setPackageBlocked(packageName: String, blocked: Boolean) {
        store.edit {
            val current = it[BLOCKED_PACKAGES].orEmpty()
            it[BLOCKED_PACKAGES] = if (blocked) current + packageName else current - packageName
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private companion object {
        val MAX_TILT = floatPreferencesKey("max_tilt_degrees")
        val REQUIRE_CHARGING = booleanPreferencesKey("require_charging")
        val MIN_BATTERY = intPreferencesKey("min_battery_percent_off_charger")
        val BRIGHTNESS_MIN = floatPreferencesKey("brightness_min")
        val BRIGHTNESS_MAX = floatPreferencesKey("brightness_max")
        val LAYOUT = stringPreferencesKey("screen_layout")
        val WIDGET_ID = intPreferencesKey("widget_id")
        val BANNERS_ENABLED = booleanPreferencesKey("notification_banners_enabled")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("notification_blocked_packages")
        val HIDE_SYSTEM_HEADS_UP = booleanPreferencesKey("hide_system_heads_up")
    }
}
