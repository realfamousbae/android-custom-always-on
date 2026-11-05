package com.realfamousbae.standby.dock.core.trigger

import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сценарии из PLAN.md, п. 2.3 и приёмочного чек-листа. Время идёт вручную. */
class TriggerEngineTest {

    private val onSide = Gravity(9.6f, 0f, 2f) // на подставке, ~12° от вертикали
    private val flat = Gravity(0f, 0f, 9.8f) // плашмя экраном вверх
    private val portrait = Gravity(0f, 9.8f, 0f) // в руке

    /** Маленький стенд: держит состояние, время и копит эффекты. */
    private class Rig(var settings: StandbySettings = StandbySettings()) {
        var state = TriggerState()
        var now = 0L
        val effects = mutableListOf<TriggerEffect>()

        fun send(event: TriggerEvent) {
            val r = TriggerEngine.reduce(state, event, now, settings)
            state = r.state
            effects += r.effects
        }

        /** Прокрутить время, подавая показания датчика ~10 раз в секунду. */
        fun hold(g: Gravity, ms: Long) {
            val end = now + ms
            while (now < end) {
                now += 100
                send(TriggerEvent.GravityReading(g))
            }
        }

        fun advance(ms: Long) {
            now += ms
            send(TriggerEvent.Tick)
        }

        val shows get() = effects.count { it == TriggerEffect.Show }
        val hides get() = effects.filterIsInstance<TriggerEffect.Hide>()
        val phase get() = state.phase
    }

    private fun rigOnBattery(percent: Int = 80) = Rig().apply {
        send(TriggerEvent.Power(charging = false, batteryPercent = percent))
    }

    private fun rigCharging() = Rig().apply {
        send(TriggerEvent.Power(charging = true, batteryPercent = 50))
    }

    @Test
    fun `без зарядки — поставили на бок и погасили экран, режим через 1_5 с`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        assertEquals(Phase.Armed, rig.phase)
        assertTrue(TriggerEffect.SetSensorActive(true) in rig.effects)
        assertTrue(TriggerEffect.HoldWakeLock(true) in rig.effects)

        rig.hold(onSide, 1_000)
        assertEquals(0, rig.shows)
        rig.hold(onSide, 600)
        assertEquals(1, rig.shows)
        assertEquals(Phase.Showing, rig.phase)
        // Экран горит — wake lock больше не нужен, датчик нужен (чтобы заметить «сняли»).
        assertTrue(TriggerEffect.HoldWakeLock(false) in rig.effects)
        assertTrue(rig.state.sensorActive)
    }

    @Test
    fun `экран включился от самого режима — показ не сбрасывается`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.ScreenOn)
        assertEquals(Phase.Showing, rig.phase)
    }

    @Test
    fun `лежит плашмя — режим не включается, окно пробы закрывается`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(flat, TriggerEngine.PROBE_WINDOW_MS + 500)
        assertEquals(0, rig.shows)
        assertEquals(Phase.Idle, rig.phase)
        assertFalse(rig.state.sensorActive)
        assertFalse(rig.state.wakeLockHeld)
    }

    @Test
    fun `окно пробы закрывается по таймеру даже без показаний датчика`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        val tick = rig.effects.filterIsInstance<TriggerEffect.ScheduleTick>().single()
        assertEquals(TriggerEngine.PROBE_WINDOW_MS, tick.atMs)
        rig.advance(TriggerEngine.PROBE_WINDOW_MS)
        assertEquals(Phase.Idle, rig.phase)
    }

    @Test
    fun `на зарядке ждём без ограничения по времени`() {
        val rig = rigCharging()
        rig.send(TriggerEvent.ScreenOff)
        assertEquals(null, rig.state.armedUntilMs)
        rig.hold(flat, 60_000)
        assertEquals(Phase.Armed, rig.phase)
        rig.hold(onSide, 1_600)
        assertEquals(1, rig.shows)
    }

    @Test
    fun `подключили зарядку при погашенном экране на подставке — режим`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.advance(TriggerEngine.PROBE_WINDOW_MS) // окно истекло
        assertEquals(Phase.Idle, rig.phase)
        rig.send(TriggerEvent.Power(charging = true, batteryPercent = 80))
        assertEquals(Phase.Armed, rig.phase)
        rig.hold(onSide, 1_600)
        assertEquals(1, rig.shows)
    }

    @Test
    fun `датчик движения будит погашенный телефон и открывает новую пробу`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.advance(TriggerEngine.PROBE_WINDOW_MS)
        rig.send(TriggerEvent.MotionWake)
        assertEquals(Phase.Armed, rig.phase)
        rig.hold(onSide, 1_600)
        assertEquals(1, rig.shows)
    }

    @Test
    fun `экран включён — датчик движения и положение не запускают режим`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOn)
        rig.send(TriggerEvent.MotionWake)
        rig.hold(onSide, 5_000)
        assertEquals(Phase.Idle, rig.phase)
        assertEquals(0, rig.shows)
    }

    @Test
    fun `пользователь включил экран во время пробы — ожидание прекращается`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 800)
        rig.send(TriggerEvent.ScreenOn)
        rig.hold(onSide, 2_000)
        assertEquals(0, rig.shows)
        assertEquals(Phase.Idle, rig.phase)
    }

    @Test
    fun `взяли в руки — режим закрывается через 2 с`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.hold(portrait, 1_500)
        assertTrue(rig.hides.isEmpty())
        rig.hold(portrait, 600)
        assertEquals("сняли с подставки", rig.hides.single().reason)
        assertEquals(Phase.Idle, rig.phase)
        assertFalse(rig.state.suppressed)
    }

    @Test
    fun `покачивание в зоне гистерезиса не закрывает режим`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        // ~50°: больше порога 45°, но меньше 45° + 10°.
        rig.hold(Gravity(6.3f, 0f, 7.5f), 5_000)
        assertEquals(Phase.Showing, rig.phase)
    }

    @Test
    fun `разряд ниже порога без зарядки — не включается и закрывается`() {
        val low = rigOnBattery(percent = 15)
        low.send(TriggerEvent.ScreenOff)
        low.hold(onSide, 3_000)
        assertEquals(0, low.shows)

        val rig = rigOnBattery(percent = 21)
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.Power(charging = false, batteryPercent = 19))
        assertEquals(1, rig.hides.size)
    }

    @Test
    fun `на зарядке порог заряда не действует`() {
        val rig = Rig()
        rig.send(TriggerEvent.Power(charging = true, batteryPercent = 3))
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        assertEquals(1, rig.shows)
    }

    @Test
    fun `только на зарядке — без неё не включается, отключили — закрылся`() {
        val rig = Rig(StandbySettings(requireCharging = true))
        rig.send(TriggerEvent.Power(charging = false, batteryPercent = 90))
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 3_000)
        assertEquals(0, rig.shows)

        rig.send(TriggerEvent.Power(charging = true, batteryPercent = 90))
        rig.hold(onSide, 1_600)
        assertEquals(1, rig.shows)
        rig.send(TriggerEvent.Power(charging = false, batteryPercent = 90))
        assertEquals(1, rig.hides.size)
    }

    @Test
    fun `вышли тапом — после погасания экрана режим не возвращается`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.UserExit)
        assertEquals(Phase.Idle, rig.phase)
        assertTrue(rig.state.suppressed)
        assertTrue("UserExit не должен слать Hide обратно", rig.hides.isEmpty())

        rig.send(TriggerEvent.ScreenOff) // экран блокировки погас по таймауту
        rig.hold(onSide, 5_000)
        assertEquals(1, rig.shows)
    }

    @Test
    fun `после выхода тапом сняли с подставки и поставили снова — режим работает`() {
        val rig = rigCharging()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.UserExit)
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 3_000)
        assertEquals(1, rig.shows)

        rig.hold(portrait, 2_100) // сняли
        assertFalse(rig.state.suppressed)
        rig.hold(onSide, 1_600) // поставили обратно
        assertEquals(2, rig.shows)
    }

    @Test
    fun `разблокировка при показанном режиме закрывает его и снимает запрет`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.UserPresent)
        assertEquals("телефон разблокирован", rig.hides.single().reason)
        assertFalse(rig.state.suppressed)
        assertFalse(rig.state.sensorActive)
    }

    @Test
    fun `кнопка питания при показанном режиме — закрыть и не возвращать`() {
        val rig = rigCharging()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.ScreenOn)
        rig.send(TriggerEvent.ScreenOff)
        assertEquals(1, rig.hides.size)
        assertTrue(rig.state.suppressed)
        rig.hold(onSide, 5_000)
        assertEquals(1, rig.shows)
    }

    @Test
    fun `отключение зарядки снимает запрет`() {
        val rig = rigCharging()
        rig.send(TriggerEvent.ScreenOff)
        rig.hold(onSide, 1_600)
        rig.send(TriggerEvent.UserExit)
        rig.send(TriggerEvent.Power(charging = false, batteryPercent = 50))
        assertFalse(rig.state.suppressed)
    }

    @Test
    fun `видео в ландшафте на зарядке — экран горит, режим не появляется`() {
        val rig = rigCharging()
        rig.send(TriggerEvent.ScreenOn)
        rig.send(TriggerEvent.UserPresent)
        rig.hold(onSide, 30_000)
        assertEquals(0, rig.shows)
        assertFalse(rig.state.sensorActive)
    }

    @Test
    fun `ручной показ игнорирует положение и закрывается только пользователем`() {
        val rig = rigOnBattery()
        rig.send(TriggerEvent.ManualShow)
        assertEquals(1, rig.shows)
        assertFalse(rig.state.sensorActive)
        rig.hold(portrait, 5_000)
        assertEquals(Phase.Showing, rig.phase)
        rig.send(TriggerEvent.UserExit)
        assertEquals(Phase.Idle, rig.phase)
    }

    @Test
    fun `повторные события питания не плодят эффекты`() {
        val rig = rigOnBattery()
        rig.effects.clear()
        repeat(5) { rig.send(TriggerEvent.Power(charging = false, batteryPercent = 80)) }
        assertTrue(rig.effects.isEmpty())
    }
}
