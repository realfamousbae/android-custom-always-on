package com.realfamousbae.standby.dock.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.realfamousbae.standby.dock.graph

/**
 * Единственная точка запуска StandbyService. Все входы (Activity, BootReceiver,
 * KeepAliveWorker, плитка) идут через неё, поэтому отказ системы запустить FGS из фона
 * всегда попадёт в журнал диагностики — это ровно то, что проверяет spike, п. 7.
 */
object ServiceStarter {

    fun start(context: Context, reason: String, action: String? = null) {
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
