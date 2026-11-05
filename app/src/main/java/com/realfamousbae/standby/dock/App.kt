package com.realfamousbae.standby.dock

import android.app.Application
import android.content.Context
import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import com.realfamousbae.standby.dock.diag.DiagLog
import com.realfamousbae.standby.dock.notifications.NotificationBus
import com.realfamousbae.standby.dock.prefs.SettingsRepository
import com.realfamousbae.standby.dock.service.KeepAliveWorker
import com.realfamousbae.standby.dock.service.Notifications
import com.realfamousbae.standby.dock.standby.StandbyController
import com.realfamousbae.standby.dock.widgets.StandbyWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File

class App : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        KeepAliveWorker.schedule(this)
    }
}

/**
 * Ручной контейнер зависимостей (без DI-фреймворка). Живёт столько же, сколько процесс.
 * Сюда по мере работы добавляются репозитории, настройки и т. п.; компоненты Android
 * (сервис, Activity, receiver'ы) получают зависимости только отсюда.
 */
class AppGraph(app: Application) {
    /** Область корутин уровня процесса (для общих StateFlow). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val diagLog = DiagLog(File(app.filesDir, "diag.log"))
    val settingsRepository = SettingsRepository(app)
    val controller = StandbyController()
    val widgetHost = StandbyWidgetHost(app)
    val notificationBus = NotificationBus()

    /** Последние настройки, всегда доступные синхронно (до первой загрузки — по умолчанию). */
    val settings: StateFlow<StandbySettings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, StandbySettings())
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
