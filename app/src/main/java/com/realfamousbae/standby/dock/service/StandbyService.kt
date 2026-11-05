package com.realfamousbae.standby.dock.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.realfamousbae.standby.dock.core.trigger.OrientationClassifier
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.sensors.GravitySensor
import com.realfamousbae.standby.dock.standby.StandbyActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground-служба, которая решает, когда показать режим StandBy.
 *
 * Версия spike (этап 1): по подключению зарядки через 2 с запускает StandbyActivity,
 * если телефон заблокирован или экран погашен, и пишет в журнал, доходят ли события
 * акселерометра при погашенном экране. На этапе 2 решение переедет в TriggerEngine
 * из :core, а служба останется тонким слоем: собирает входы, исполняет решение.
 */
class StandbyService : LifecycleService() {

    private val log get() = graph.diagLog
    private lateinit var gravitySensor: GravitySensor
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    private var launchJob: Job? = null
    private var sensorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val systemEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onPowerConnected()
                Intent.ACTION_POWER_DISCONNECTED -> onPowerDisconnected()
                else -> log.log("Событие: ${intent.action?.substringAfterLast('.')}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(PowerManager::class.java)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        gravitySensor = GravitySensor(getSystemService(SensorManager::class.java))
        goForeground()

        // POWER_CONNECTED/DISCONNECTED нельзя получить через манифест (Android 8+),
        // поэтому слушаем их здесь, пока жива служба.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, systemEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        log.log("Служба создана. Датчик: ${gravitySensor.description}")
        if (isChargingNow()) startSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // При каждом startForegroundService система ждёт startForeground — вызываем повторно.
        goForeground()
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "перезапуск системой"
        log.log("Служба: старт ($reason)")
        if (intent?.action == ACTION_TEST_LAUNCH) {
            launchJob?.cancel()
            launchJob = lifecycleScope.launch {
                log.log("Тест: запуск режима через ${TEST_LAUNCH_DELAY_MS / 1000} с — заблокируйте экран")
                delay(TEST_LAUNCH_DELAY_MS)
                launchStandby("тест", requireLocked = false)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(systemEvents)
        stopSensors()
        log.log("Служба остановлена")
        super.onDestroy()
    }

    private fun goForeground() {
        // Тип specialUse появился в Android 14; на 12–13 тип не передаём.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.ID_SERVICE, Notifications.service(this), type)
    }

    private fun onPowerConnected() {
        log.log("Зарядка подключена (${chargeSource()})")
        startSensors()
        launchJob?.cancel()
        launchJob = lifecycleScope.launch {
            delay(POWER_LAUNCH_DELAY_MS)
            launchStandby("зарядка", requireLocked = true)
        }
    }

    private fun onPowerDisconnected() {
        log.log("Зарядка отключена")
        launchJob?.cancel()
        stopSensors()
    }

    private fun launchStandby(reason: String, requireLocked: Boolean) {
        val locked = keyguardManager.isKeyguardLocked
        val interactive = powerManager.isInteractive
        val overlay = Settings.canDrawOverlays(this)
        log.log("Запуск режима ($reason): locked=$locked, interactive=$interactive, overlay=$overlay")
        if (requireLocked && !locked && interactive) {
            log.log("Пропуск: телефон разблокирован и экран включён")
            return
        }
        startActivity(StandbyActivity.intent(this))
    }

    /** Spike, п. 5: проверяем, доходят ли события акселерометра при погашенном экране. */
    private fun startSensors() {
        if (sensorJob?.isActive == true) return
        if (!gravitySensor.isAvailable) {
            log.log("Акселерометра нет")
            return
        }
        if (!gravitySensor.isWakeUp) acquireWakeLock()
        sensorJob = lifecycleScope.launch {
            var count = 0
            var maxGapMs = 0L
            var lastEventAt = SystemClock.elapsedRealtime()
            var windowStart = lastEventAt
            gravitySensor.gravity().collect { g ->
                val now = SystemClock.elapsedRealtime()
                maxGapMs = maxOf(maxGapMs, now - lastEventAt)
                lastEventAt = now
                count++
                if (now - windowStart >= SENSOR_REPORT_PERIOD_MS) {
                    val tilt = OrientationClassifier.tiltFromVerticalDegrees(g)
                    log.log(
                        String.format(
                            Locale.ROOT,
                            "Акселерометр: %d событий за %d с, макс. пауза %d мс, экран %s, наклон %s",
                            count, (now - windowStart) / 1000, maxGapMs,
                            if (powerManager.isInteractive) "вкл" else "выкл",
                            tilt?.let { "%.0f°".format(Locale.ROOT, it) } ?: "—",
                        ),
                    )
                    count = 0
                    maxGapMs = 0
                    windowStart = now
                }
            }
        }
    }

    private fun stopSensors() {
        sensorJob?.cancel()
        sensorJob = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun acquireWakeLock() {
        log.log("Wake-up акселерометра нет — держим PARTIAL_WAKE_LOCK на время зарядки")
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StandBy:sensors")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun batteryStatus(): Intent? =
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    private fun isChargingNow(): Boolean =
        (batteryStatus()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    private fun chargeSource(): String =
        when (batteryStatus()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "беспроводная"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "док"
            else -> "неизвестно"
        }

    companion object {
        const val EXTRA_REASON = "reason"
        const val ACTION_TEST_LAUNCH = "com.realfamousbae.standby.dock.TEST_LAUNCH"

        private const val POWER_LAUNCH_DELAY_MS = 2_000L
        private const val TEST_LAUNCH_DELAY_MS = 10_000L
        private const val SENSOR_REPORT_PERIOD_MS = 10_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L
    }
}
