package com.realfamousbae.standby.dock.service

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.realfamousbae.standby.dock.core.trigger.Phase
import com.realfamousbae.standby.dock.core.trigger.TriggerEffect
import com.realfamousbae.standby.dock.core.trigger.TriggerEngine
import com.realfamousbae.standby.dock.core.trigger.TriggerEvent
import com.realfamousbae.standby.dock.core.trigger.TriggerState
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.sensors.GravitySensor
import com.realfamousbae.standby.dock.sensors.MotionWakeSensor
import com.realfamousbae.standby.dock.standby.StandbyActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Foreground-служба: собирает входы (экран, питание, датчики, действия пользователя),
 * отдаёт их в [TriggerEngine] из :core и исполняет его эффекты. Собственной логики
 * «когда показывать» здесь нет — она вся в TriggerEngine и покрыта unit-тестами.
 *
 * Все события проходят через один канал и обрабатываются по очереди на главном потоке,
 * поэтому состояние не требует синхронизации.
 */
class StandbyService : LifecycleService() {

    private val log get() = graph.diagLog
    private lateinit var powerManager: PowerManager
    private lateinit var gravitySensor: GravitySensor
    private lateinit var motionWakeSensor: MotionWakeSensor

    private val events = Channel<TriggerEvent>(Channel.UNLIMITED)
    private var state = TriggerState()

    private var gravityJob: Job? = null
    private var tickJob: Job? = null
    private var wakeLockRefreshJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var testLaunchJob: Job? = null
    private var lastLoggedTilt: Int? = null

    private val systemEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> events.trySend(TriggerEvent.ScreenOff)
                Intent.ACTION_SCREEN_ON -> events.trySend(TriggerEvent.ScreenOn)
                Intent.ACTION_USER_PRESENT -> events.trySend(TriggerEvent.UserPresent)
                Intent.ACTION_BATTERY_CHANGED -> events.trySend(powerEvent(intent))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(PowerManager::class.java)
        val sensorManager = getSystemService(SensorManager::class.java)
        gravitySensor = GravitySensor(sensorManager)
        motionWakeSensor = MotionWakeSensor(sensorManager)
        goForeground()

        // Экран и питание нельзя получить через манифест — слушаем, пока жива служба.
        // BATTERY_CHANGED «липкий»: при регистрации сразу приходит текущее состояние.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(this, systemEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        log.log(
            "Служба создана. Акселерометр: ${gravitySensor.description}; " +
                "датчик движения: ${motionWakeSensor.description}",
        )

        lifecycleScope.launch { for (event in events) handle(event) }
        lifecycleScope.launch {
            graph.controller.userEvents.collect { event ->
                // Горит ли экран, знает только система: при нажатии кнопки питания Activity
                // может уйти раньше, чем дойдёт SCREEN_OFF.
                events.send(
                    if (event is TriggerEvent.UserExit) event.copy(screenOn = powerManager.isInteractive) else event,
                )
            }
        }
        lifecycleScope.launch {
            motionWakeSensor.events().collect { events.send(TriggerEvent.MotionWake) }
        }
        // Если служба поднялась при погашенном экране (загрузка, воркер) — сразу проба.
        if (!powerManager.isInteractive) events.trySend(TriggerEvent.ScreenOff)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // При каждом startForegroundService система ждёт startForeground — вызываем повторно.
        goForeground()
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "перезапуск системой"
        log.log("Служба: старт ($reason)")
        if (intent?.action == ACTION_TEST_LAUNCH) {
            testLaunchJob?.cancel()
            testLaunchJob = lifecycleScope.launch {
                log.log("Тест: режим через ${TEST_LAUNCH_DELAY_MS / 1000} с — заблокируйте экран")
                delay(TEST_LAUNCH_DELAY_MS)
                events.send(TriggerEvent.ManualShow)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(systemEvents)
        releaseWakeLock()
        graph.controller.setVisible(false)
        log.log("Служба остановлена")
        super.onDestroy()
    }

    private fun handle(event: TriggerEvent) {
        val before = state
        val result = TriggerEngine.reduce(before, event, SystemClock.elapsedRealtime(), graph.settings.value)
        state = result.state
        logTransition(before, state, event)
        result.effects.forEach(::apply)
    }

    private fun apply(effect: TriggerEffect) {
        when (effect) {
            is TriggerEffect.SetSensorActive -> setGravityActive(effect.active)
            is TriggerEffect.HoldWakeLock -> if (effect.hold) acquireWakeLock() else releaseWakeLock()
            is TriggerEffect.ScheduleTick -> {
                tickJob?.cancel()
                tickJob = lifecycleScope.launch {
                    delay((effect.atMs - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    events.send(TriggerEvent.Tick)
                }
            }
            TriggerEffect.Show -> {
                graph.controller.setVisible(true)
                log.log("Режим: показать")
                // Фоновый запуск разрешён правом «Поверх других окон» (BAL_ALLOW_SAW_PERMISSION).
                startActivity(StandbyActivity.intent(this))
            }
            is TriggerEffect.Hide -> {
                log.log("Режим: скрыть (${effect.reason})")
                graph.controller.setVisible(false)
            }
        }
    }

    private fun setGravityActive(active: Boolean) {
        gravityJob?.cancel()
        gravityJob = null
        lastLoggedTilt = null
        if (!active) return
        if (!gravitySensor.isAvailable) {
            log.log("Акселерометра нет — автозапуск по положению невозможен")
            return
        }
        gravityJob = lifecycleScope.launch {
            gravitySensor.gravity().collect { events.send(TriggerEvent.GravityReading(it)) }
        }
    }

    /**
     * Wake-up акселерометра на S24 Ultra нет, поэтому пока ждём положение при погашенном
     * экране, держим PARTIAL_WAKE_LOCK. Он не считается по ссылкам и перевзводится с
     * таймаутом, пока нужен: если служба умрёт, система отпустит его сама.
     */
    private fun acquireWakeLock() {
        val lock = wakeLock ?: powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StandBy:trigger")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        wakeLockRefreshJob?.cancel()
        wakeLockRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(WAKE_LOCK_REFRESH_MS)
                lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLockRefreshJob?.cancel()
        wakeLockRefreshJob = null
        wakeLock?.takeIf { it.isHeld }?.release()
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

    private fun powerEvent(intent: Intent): TriggerEvent.Power {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else 100
        return TriggerEvent.Power(charging = plugged, batteryPercent = percent)
    }

    /** В журнал — только смена фазы, питания и заметные изменения угла, а не каждое событие. */
    private fun logTransition(before: TriggerState, after: TriggerState, event: TriggerEvent) {
        if (before.charging != after.charging) {
            log.log("Питание: ${if (after.charging) "зарядка" else "батарея"}, ${after.batteryPercent}%")
        }
        if (before.phase != after.phase) {
            val cause = event::class.simpleName
            log.log("Фаза: ${before.phase} → ${after.phase} ($cause)${phaseNote(after)}")
        }
        if (before.suppressedUntilScreenOn != after.suppressedUntilScreenOn) {
            log.log(if (after.suppressedUntilScreenOn) "Запрет показа до включения экрана" else "Запрет снят")
        }
        val tilt = after.lastTiltDegrees?.roundToInt()
        if (after.sensorActive && tilt != null && after.phase == Phase.Armed) {
            val last = lastLoggedTilt
            if (last == null || kotlin.math.abs(tilt - last) >= TILT_LOG_STEP) {
                lastLoggedTilt = tilt
                log.log(String.format(Locale.ROOT, "Наклон от вертикали: %d°", tilt))
            }
        }
    }

    private fun phaseNote(s: TriggerState): String = when (s.phase) {
        Phase.Armed -> if (s.armedUntilMs == null) ", ждём без ограничения" else ", проба 10 с"
        else -> ""
    }

    companion object {
        const val EXTRA_REASON = "reason"
        const val ACTION_TEST_LAUNCH = "com.realfamousbae.standby.dock.TEST_LAUNCH"

        private const val TEST_LAUNCH_DELAY_MS = 10_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 5 * 60 * 1000L
        private const val TILT_LOG_STEP = 10
    }
}
