package com.realfamousbae.standby.dock.service

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.quicksettings.TileService
import com.realfamousbae.standby.dock.core.run.LastExit
import com.realfamousbae.standby.dock.core.run.RunPolicy
import com.realfamousbae.standby.dock.diag.DiagLog
import com.realfamousbae.standby.dock.notifications.StandbyNotificationListener
import com.realfamousbae.standby.dock.prefs.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Главный выключатель StandBy и уважение к внешней остановке приложения.
 *
 * При старте процесса один раз смотрит, как умер прошлый процесс (ApplicationExitInfo): если
 * пользователь остановил приложение принудительно, тумблер переходит в «Выкл» (решение — в
 * [RunPolicy] из :core). Все автоматические пути запуска службы ждут окончания этой проверки
 * ([autoStartAllowed]), поэтому успеть поднять службу до неё не может никто.
 */
class RunState(
    private val app: Application,
    private val settings: SettingsRepository,
    private val log: DiagLog,
) {
    private val ready = CompletableDeferred<Unit>()

    /** Вызывается один раз из App.onCreate. */
    fun startup(scope: CoroutineScope) {
        scope.launch {
            try {
                checkLastExit()
            } catch (e: Exception) {
                log.log("Запуск: не удалось прочитать причину прошлого выхода: $e")
            } finally {
                ready.complete(Unit)
            }
            syncBackgroundWork(settings.settings.first().enabled)
        }
    }

    /** Можно ли поднимать службу автоматически. Ждёт проверки прошлого выхода. */
    suspend fun autoStartAllowed(): Boolean {
        ready.await()
        return RunPolicy.autoStartAllowed(settings.settings.first().enabled)
    }

    /** Включение/выключение пользователем (тумблер в настройках, плитка в шторке). */
    suspend fun setEnabled(on: Boolean, reason: String) {
        settings.setEnabled(on)
        log.log(if (on) "StandBy включён ($reason)" else "StandBy выключен ($reason)")
        syncBackgroundWork(on)
        if (on) {
            ServiceStarter.start(app, reason = reason, userInitiated = true)
            runCatching {
                NotificationListenerService.requestRebind(StandbyNotificationListener.component(app))
            }
        } else {
            app.stopService(Intent(app, StandbyService::class.java))
        }
        refreshTile()
    }

    private suspend fun checkLastExit() {
        val am = app.getSystemService(ActivityManager::class.java)
        val info = am.getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull() ?: return
        val userRequested = info.reason == ApplicationExitInfo.REASON_USER_REQUESTED
        val kind = RunPolicy.classify(userRequested, info.description)
        val decision = RunPolicy.onProcessStart(LastExit(kind, info.timestamp), settings.lastHandledExitMs())
        val handled = decision.markHandledMs ?: return
        log.log("Прошлый выход процесса: причина ${info.reason}, «${info.description.orEmpty()}» → $kind")
        if (decision.disable) {
            settings.setEnabled(false, external = true)
            log.log("StandBy выключен: приложение было остановлено принудительно")
            app.stopService(Intent(app, StandbyService::class.java))
            refreshTile()
        }
        settings.setLastHandledExitMs(handled)
    }

    private fun syncBackgroundWork(enabled: Boolean) {
        if (enabled) KeepAliveWorker.schedule(app) else KeepAliveWorker.cancel(app)
    }

    private fun refreshTile() {
        runCatching { TileService.requestListeningState(app, ComponentName(app, StandbyTileService::class.java)) }
    }
}
