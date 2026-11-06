package com.realfamousbae.standby.dock.core.run

/** Как закончился прошлый процесс приложения — в той мере, в какой это важно для автозапуска. */
enum class ExitKind {
    /** Пользователь явно остановил приложение (Force stop, «Остановить» в диспетчере задач). */
    UserStopped,

    /** Всё остальное: нехватка памяти, сбой, свайп из «Недавних», обновление и т. п. */
    Other,
}

/** Последняя запись о завершении процесса (ApplicationExitInfo в слое :app). */
data class LastExit(val kind: ExitKind, val timestampMs: Long)

/** Что сделать при старте процесса. [markHandledMs] — сохранить как «уже учтённый» выход. */
data class ExitDecision(val disable: Boolean, val markHandledMs: Long?)

/**
 * Правила «работать ли в фоне». Внешняя остановка приложения считается штатным выключением:
 * тумблер переходит в «Выкл», и никакой автоматический путь (воркер, загрузка, перезапуск
 * службы системой, открытие приложения) службу не поднимает, пока пользователь не включит её сам.
 */
object RunPolicy {

    /**
     * У Android `REASON_USER_REQUESTED` означает и остановку приложения (Force stop —
     * «stop <pkg> due to from pid N», «Остановить» в диспетчере задач — «fully stop <pkg>/0 by
     * user request», очистка памяти — «kill background»), и свайп из «Недавних» («remove task»),
     * а подпричины (`SUBREASON_*`) в публичном API нет. Поэтому любой `USER_REQUESTED` считаем
     * остановкой, кроме явно опознанных свайпа и обновления пакета (на API 31–32 обновление
     * тоже приходит как USER_REQUESTED с «due to update pkg»/«replace pkg»).
     * Строки — из ActivityManagerService (AOSP).
     */
    fun classify(reasonIsUserRequested: Boolean, description: String?): ExitKind {
        if (!reasonIsUserRequested) return ExitKind.Other
        val d = description?.lowercase().orEmpty()
        return if (NOT_A_STOP_MARKERS.any { it in d }) ExitKind.Other else ExitKind.UserStopped
    }

    /**
     * Решение при старте процесса. Каждый выход учитывается один раз: после того как
     * пользователь снова включил режим, старая запись о Force stop не выключит его повторно.
     */
    fun onProcessStart(lastExit: LastExit?, lastHandledExitMs: Long): ExitDecision {
        if (lastExit == null || lastExit.timestampMs <= lastHandledExitMs) return ExitDecision(false, null)
        return ExitDecision(disable = lastExit.kind == ExitKind.UserStopped, markHandledMs = lastExit.timestampMs)
    }

    /** Можно ли поднимать службу автоматически (не по прямому действию пользователя). */
    fun autoStartAllowed(enabled: Boolean): Boolean = enabled

    private val NOT_A_STOP_MARKERS = listOf("remove task", "update pkg", "replace pkg")
}
