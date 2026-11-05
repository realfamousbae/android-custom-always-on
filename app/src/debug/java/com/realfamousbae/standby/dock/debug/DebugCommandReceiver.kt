package com.realfamousbae.standby.dock.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.realfamousbae.standby.dock.graph
import com.realfamousbae.standby.dock.service.KeepAliveWorker
import com.realfamousbae.standby.dock.service.ServiceStarter
import com.realfamousbae.standby.dock.service.StandbyService
import java.util.concurrent.TimeUnit

/**
 * Отладочные команды через adb (только debug-сборка), например:
 * `adb shell am broadcast -a com.realfamousbae.standby.dock.debug.RUN_KEEP_ALIVE -p com.realfamousbae.standby.dock`
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val log = context.graph.diagLog
        when (intent.action?.substringAfterLast('.')) {
            "STOP_SERVICE" -> {
                context.stopService(Intent(context, StandbyService::class.java))
                log.log("adb: служба остановлена")
            }
            // Разовая задача с тем же воркером: проверяем старт FGS из фонового Worker'а,
            // минуя защиту WorkManager от досрочного запуска периодической задачи.
            // Задержка 15 с — чтобы к запуску воркера процесс уже был «в фоне», а не
            // обрабатывал этот broadcast.
            "RUN_KEEP_ALIVE" -> {
                val request = OneTimeWorkRequestBuilder<KeepAliveWorker>()
                    .setInitialDelay(15, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueue(request)
                log.log("adb: разовый KeepAliveWorker через 15 с")
            }
            "SHOW_STANDBY" ->
                ServiceStarter.start(context, "adb", StandbyService.ACTION_TEST_LAUNCH)
        }
    }
}
