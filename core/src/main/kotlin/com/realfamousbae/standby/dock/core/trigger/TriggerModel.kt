package com.realfamousbae.standby.dock.core.trigger

/** Входы TriggerEngine. Служба переводит в них системные события и показания датчиков. */
sealed interface TriggerEvent {
    data object ScreenOff : TriggerEvent
    data object ScreenOn : TriggerEvent

    /** Пользователь разблокировал телефон. */
    data object UserPresent : TriggerEvent

    /** Состояние питания (приходит часто, дубликаты допустимы). */
    data class Power(val charging: Boolean, val batteryPercent: Int) : TriggerEvent

    data class GravityReading(val gravity: Gravity) : TriggerEvent

    /** Сработал wake-up датчик движения при погашенном экране — стоит перепроверить положение. */
    data object MotionWake : TriggerEvent

    /** Сработал таймер, заказанный эффектом ScheduleTick. */
    data object Tick : TriggerEvent

    /** Режим закрыт пользователем (тап, кнопка питания, Activity ушла с экрана). */
    data object UserExit : TriggerEvent

    /** Показать режим немедленно, без проверки условий (кнопка теста, плитка). */
    data object ManualShow : TriggerEvent
}

/** Что служба должна сделать. TriggerEngine сам ничего не вызывает. */
sealed interface TriggerEffect {
    data class SetSensorActive(val active: Boolean) : TriggerEffect
    data class HoldWakeLock(val hold: Boolean) : TriggerEffect
    data class ScheduleTick(val atMs: Long) : TriggerEffect
    data object Show : TriggerEffect
    data class Hide(val reason: String) : TriggerEffect
}

enum class Phase {
    /** Телефоном пользуются (экран включён) либо ждать нечего. */
    Idle,

    /** Экран погас — слушаем акселерометр и ждём устойчивого положения «на боку». */
    Armed,

    /** Режим показан. */
    Showing,
}

data class TriggerState(
    val phase: Phase = Phase.Idle,
    val charging: Boolean = false,
    val batteryPercent: Int = 100,
    val screenInteractive: Boolean = true,
    /** Граница окна пробы в фазе Armed; null — без ограничения (на зарядке). */
    val armedUntilMs: Long? = null,
    /** С какого момента телефон непрерывно «на боку» (для дебаунса входа). */
    val onSideSinceMs: Long? = null,
    /** С какого момента телефон непрерывно «не на боку» с учётом гистерезиса (для выхода). */
    val offSideSinceMs: Long? = null,
    val lastTiltDegrees: Float? = null,
    /** Пользователь закрыл режим; не показывать, пока телефон не сняли с бока. */
    val suppressed: Boolean = false,
    /** Режим показан вручную: закрывается только пользователем. */
    val manual: Boolean = false,
    // Что сейчас реально включено у службы — чтобы выдавать эффекты только при изменениях.
    val sensorActive: Boolean = false,
    val wakeLockHeld: Boolean = false,
)
