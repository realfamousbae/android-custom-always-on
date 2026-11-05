package com.realfamousbae.standby.dock.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Проверки системных разрешений и настроек. Основа будущего мастера разрешений (этап 5). */
object SystemChecks {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /** До Android 13 разрешения на уведомления не было — считаем выданным. */
    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Включена ли системная «Заставка» (Settings.Secure `screensaver_enabled`).
     * Ключ скрытый; если прошивка не даёт его прочитать — null («неизвестно»).
     */
    fun isScreensaverEnabled(context: Context): Boolean? = try {
        Settings.Secure.getInt(context.contentResolver, "screensaver_enabled") == 1
    } catch (_: Exception) {
        null
    }

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    fun overlaySettings(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    /**
     * Системный диалог «Разрешить работу в фоне без ограничений?». Для Google Play такое
     * запрещено, но приложение ставится только вручную (см. docs/PLAN.md).
     */
    @android.annotation.SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    /** Карточка приложения: на One UI оттуда «Батарея → Без ограничений». */
    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    fun screensaverSettings() = Intent(Settings.ACTION_DREAM_SETTINGS)
}
