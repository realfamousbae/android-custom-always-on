package com.realfamousbae.standby.dock.core.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPolicyTest {

    @Test
    fun `принудительная остановка и диспетчер задач распознаются`() {
        // Force stop в настройках приложения.
        assertEquals(ExitKind.UserStopped, RunPolicy.classify(true, "stop com.example due to from pid 1234"))
        // «Остановить» в списке активных приложений (Android 13+).
        assertEquals(ExitKind.UserStopped, RunPolicy.classify(true, "fully stop com.example/0 by user request"))
        // Очистка памяти другим приложением.
        assertEquals(ExitKind.UserStopped, RunPolicy.classify(true, "kill background"))
        // Неизвестная формулировка прошивки — тоже остановка по запросу пользователя.
        assertEquals(ExitKind.UserStopped, RunPolicy.classify(true, null))
    }

    @Test
    fun `свайп из недавних и обновление не считаются остановкой`() {
        assertEquals(ExitKind.Other, RunPolicy.classify(true, "remove task"))
        assertEquals(ExitKind.Other, RunPolicy.classify(true, "stop com.example due to update pkg"))
        assertEquals(ExitKind.Other, RunPolicy.classify(true, "stop com.example due to replace pkg"))
    }

    @Test
    fun `другие причины выхода — не остановка`() {
        assertEquals(ExitKind.Other, RunPolicy.classify(false, "stop com.example due to from pid 1234"))
        assertEquals(ExitKind.Other, RunPolicy.classify(false, null))
    }

    @Test
    fun `новая остановка выключает и помечается учтённой`() {
        val d = RunPolicy.onProcessStart(LastExit(ExitKind.UserStopped, 1_000), lastHandledExitMs = 500)
        assertTrue(d.disable)
        assertEquals(1_000L, d.markHandledMs)
    }

    @Test
    fun `уже учтённая остановка не выключает повторно`() {
        val d = RunPolicy.onProcessStart(LastExit(ExitKind.UserStopped, 1_000), lastHandledExitMs = 1_000)
        assertFalse(d.disable)
        assertNull(d.markHandledMs)
    }

    @Test
    fun `обычная смерть процесса не выключает`() {
        val d = RunPolicy.onProcessStart(LastExit(ExitKind.Other, 2_000), lastHandledExitMs = 0)
        assertFalse(d.disable)
        assertEquals(2_000L, d.markHandledMs)
        assertFalse(RunPolicy.onProcessStart(null, 0).disable)
    }
}
