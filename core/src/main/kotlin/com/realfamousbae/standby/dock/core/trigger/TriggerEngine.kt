package com.realfamousbae.standby.dock.core.trigger

import com.realfamousbae.standby.dock.core.prefs.StandbySettings

/**
 * Чистая логика «когда показывать режим StandBy». Без Android, без часов и потоков:
 * `reduce(state, event, nowMs, settings)` возвращает новое состояние и список эффектов,
 * которые исполняет служба. Поэтому всё поведение проверяется unit-тестами.
 *
 * Правила (подробно — PLAN.md, п. 2.3):
 * - Вход только по фронтам: экран погас, подключили зарядку при погашенном экране,
 *   wake-up датчик движения при погашенном экране. Пока экран горит, режим не всплывает.
 * - После фронта — фаза Armed: слушаем акселерометр; на зарядке без ограничения по
 *   времени, без зарядки — окно пробы [PROBE_WINDOW_MS], чтобы не держать датчик и
 *   wake lock всю ночь.
 * - Показ: на боку непрерывно [ENTER_STABLE_MS] + допустимое питание + не suppressed.
 * - Скрытие: сняли с бока ([EXIT_STABLE_MS] за порогом + [EXIT_HYSTERESIS_DEGREES]),
 *   заряд упал ниже порога без зарядки, разблокировали телефон, закрыли жестом.
 * - После закрытия пользователем — suppressed, пока телефон не сняли с бока, не изменилось
 *   состояние зарядки или его не разблокировали.
 */
object TriggerEngine {

    const val ENTER_STABLE_MS = 1_500L
    const val EXIT_STABLE_MS = 2_000L
    const val PROBE_WINDOW_MS = 10_000L
    const val EXIT_HYSTERESIS_DEGREES = 10f

    data class Result(val state: TriggerState, val effects: List<TriggerEffect>)

    fun reduce(
        state: TriggerState,
        event: TriggerEvent,
        nowMs: Long,
        settings: StandbySettings,
    ): Result {
        val effects = mutableListOf<TriggerEffect>()
        var s = state

        when (event) {
            TriggerEvent.ScreenOff -> {
                s = s.copy(screenInteractive = false)
                s = if (s.phase == Phase.Showing) {
                    // Экран режима погасили кнопкой питания — это явное «выключи».
                    hide(s, effects, "погасили кнопкой питания", suppress = true)
                } else {
                    arm(s, nowMs, effects)
                }
            }

            TriggerEvent.ScreenOn -> {
                s = s.copy(screenInteractive = true)
                // Экран включил пользователь — перестаём ждать. Если экран включили мы
                // сами (turnScreenOn при показе), фаза уже Showing и ничего не меняется.
                if (s.phase == Phase.Armed) s = s.copy(phase = Phase.Idle, armedUntilMs = null)
            }

            TriggerEvent.UserPresent -> {
                s = s.copy(screenInteractive = true, suppressed = false)
                s = when (s.phase) {
                    Phase.Showing -> hide(s, effects, "телефон разблокирован", suppress = false)
                    Phase.Armed -> s.copy(phase = Phase.Idle, armedUntilMs = null)
                    Phase.Idle -> s
                }
            }

            is TriggerEvent.Power -> {
                val plugged = event.charging && !s.charging
                val chargingChanged = event.charging != s.charging
                s = s.copy(charging = event.charging, batteryPercent = event.batteryPercent)
                if (chargingChanged) s = s.copy(suppressed = false)
                if (chargingChanged && s.phase == Phase.Armed) {
                    // На зарядке ждём без ограничения, без неё — заново открываем окно пробы.
                    s = s.copy(armedUntilMs = if (s.charging) null else nowMs + PROBE_WINDOW_MS)
                    s.armedUntilMs?.let { effects += TriggerEffect.ScheduleTick(it) }
                }
                if (plugged && !s.screenInteractive && s.phase == Phase.Idle) {
                    s = arm(s, nowMs, effects)
                }
            }

            is TriggerEvent.GravityReading -> {
                if (s.sensorActive) s = trackOrientation(s, event.gravity, nowMs, settings)
            }

            TriggerEvent.MotionWake -> {
                if (!s.screenInteractive && s.phase != Phase.Showing) s = arm(s, nowMs, effects)
            }

            TriggerEvent.Tick -> Unit

            TriggerEvent.UserExit -> {
                if (s.phase == Phase.Showing) {
                    s = hide(s, effects, "закрыт пользователем", suppress = true, notify = false)
                }
            }

            TriggerEvent.ManualShow -> {
                s = s.copy(phase = Phase.Showing, manual = true, suppressed = false, armedUntilMs = null)
                effects += TriggerEffect.Show
            }
        }

        s = evaluate(s, nowMs, settings, effects)
        s = reconcileResources(s, effects)
        return Result(s, effects)
    }

    /** Перейти в ожидание положения (или продлить ожидание). */
    private fun arm(s: TriggerState, nowMs: Long, effects: MutableList<TriggerEffect>): TriggerState {
        val until = if (s.charging) null else nowMs + PROBE_WINDOW_MS
        until?.let { effects += TriggerEffect.ScheduleTick(it) }
        return s.copy(phase = Phase.Armed, armedUntilMs = until)
    }

    private fun hide(
        s: TriggerState,
        effects: MutableList<TriggerEffect>,
        reason: String,
        suppress: Boolean,
        notify: Boolean = true,
    ): TriggerState {
        if (notify) effects += TriggerEffect.Hide(reason)
        return s.copy(
            phase = Phase.Idle,
            armedUntilMs = null,
            manual = false,
            suppressed = s.suppressed || suppress,
        )
    }

    private fun trackOrientation(
        s: TriggerState,
        gravity: Gravity,
        nowMs: Long,
        settings: StandbySettings,
    ): TriggerState {
        val tilt = OrientationClassifier.tiltFromVerticalDegrees(gravity) ?: return s
        val onSide = tilt < settings.maxTiltDegrees
        val clearlyOff = tilt > settings.maxTiltDegrees + EXIT_HYSTERESIS_DEGREES
        val next = when {
            onSide -> s.copy(onSideSinceMs = s.onSideSinceMs ?: nowMs, offSideSinceMs = null)
            clearlyOff -> s.copy(onSideSinceMs = null, offSideSinceMs = s.offSideSinceMs ?: nowMs)
            // Зона гистерезиса: не стабильно ни для входа, ни для выхода.
            else -> s.copy(onSideSinceMs = null, offSideSinceMs = null)
        }
        val liftedLongEnough = next.offSideSinceMs?.let { nowMs - it >= EXIT_STABLE_MS } == true
        return next.copy(
            lastTiltDegrees = tilt,
            suppressed = next.suppressed && !liftedLongEnough,
        )
    }

    private fun evaluate(
        s: TriggerState,
        nowMs: Long,
        settings: StandbySettings,
        effects: MutableList<TriggerEffect>,
    ): TriggerState = when (s.phase) {
        Phase.Idle -> s

        Phase.Armed -> {
            val stableOnSide = s.onSideSinceMs?.let { nowMs - it >= ENTER_STABLE_MS } == true
            when {
                stableOnSide && canShow(s, settings) -> {
                    effects += TriggerEffect.Show
                    s.copy(phase = Phase.Showing, armedUntilMs = null)
                }
                s.armedUntilMs != null && nowMs >= s.armedUntilMs ->
                    s.copy(phase = Phase.Idle, armedUntilMs = null)
                else -> s
            }
        }

        Phase.Showing -> when {
            s.manual -> s
            s.offSideSinceMs?.let { nowMs - it >= EXIT_STABLE_MS } == true ->
                hide(s, effects, "сняли с подставки", suppress = false)
            !powerAllowed(s, settings) ->
                hide(s, effects, "нет зарядки или мало заряда", suppress = false)
            else -> s
        }
    }

    private fun canShow(s: TriggerState, settings: StandbySettings): Boolean =
        !s.suppressed && powerAllowed(s, settings)

    private fun powerAllowed(s: TriggerState, settings: StandbySettings): Boolean = when {
        s.charging -> true
        settings.requireCharging -> false
        else -> s.batteryPercent >= settings.minBatteryPercentOffCharger
    }

    /**
     * Датчик нужен, пока ждём положение или показываем режим (чтобы заметить, что сняли).
     * Wake lock — только пока ждём при погашенном экране: при показанном режиме экран горит
     * и процессор и так не спит.
     */
    private fun reconcileResources(s: TriggerState, effects: MutableList<TriggerEffect>): TriggerState {
        val wantSensor = s.phase == Phase.Armed || (s.phase == Phase.Showing && !s.manual)
        val wantWakeLock = s.phase == Phase.Armed
        var next = s
        if (wantSensor != s.sensorActive) {
            effects += TriggerEffect.SetSensorActive(wantSensor)
            // Старые отметки времени без свежих показаний недостоверны.
            next = next.copy(sensorActive = wantSensor, onSideSinceMs = null, offSideSinceMs = null)
        }
        if (wantWakeLock != s.wakeLockHeld) {
            effects += TriggerEffect.HoldWakeLock(wantWakeLock)
            next = next.copy(wakeLockHeld = wantWakeLock)
        }
        return next
    }
}
