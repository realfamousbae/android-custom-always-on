package com.realfamousbae.standby.dock.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.realfamousbae.standby.dock.graph
import kotlinx.coroutines.launch

/**
 * Единственная точка запуска StandbyService. Все входы (Activity, BootReceiver,
 * KeepAliveWorker, плитка) идут через неё, поэтому отказ системы запустить FGS из фона
 * всегда попадёт в журнал диагностики — это ровно то, что проверяет spike, п. 7.
 *
 * Автоматические запуски проходят через главный выключатель ([RunState.autoStartAllowed]):
 * если StandBy выключен тумблером или внешней остановкой, служба не поднимается.
 * [userInitiated] — прямое действие пользователя «включить», проверка не нужна.
 */
object ServiceStarter {

    fun start(context: Context, reason: String, action: String? = null, userInitiated: Boolean = false) {
        if (userInitiated) {
            startNow(context, reason, action)
            return
        }
        val graph = context.graph
        graph.appScope.launch { startIfAllowed(context, reason, action) }
    }

    /** Запустить, если разрешено; ждёт проверки причины прошлого выхода. */
    suspend fun startIfAllowed(context: Context, reason: String, action: String? = null): Boolean {
        val graph = context.graph
        if (!graph.runState.autoStartAllowed()) {
            graph.diagLog.log("FGS: пропуск ($reason) — StandBy выключен")
            return false
        }
        startNow(context, reason, action)
        return true
    }

    private fun startNow(context: Context, reason: String, action: String?) {
        val log = context.graph.diagLog
        val intent = Intent(context, StandbyService::class.java)
            .setAction(action)
            .putExtra(StandbyService.EXTRA_REASON, reason)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            log.log("FGS: запуск ЗАПРЕЩЁН системой ($reason): ${e.message}")
        } catch (e: Exception) {
            log.log("FGS: ошибка запуска ($reason): $e")
        }
    }
}
