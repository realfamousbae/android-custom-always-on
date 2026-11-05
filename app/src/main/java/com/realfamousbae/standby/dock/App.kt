package com.realfamousbae.standby.dock

import android.app.Application
import android.content.Context
import com.realfamousbae.standby.dock.diag.DiagLog
import com.realfamousbae.standby.dock.service.KeepAliveWorker
import com.realfamousbae.standby.dock.service.Notifications
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
    val diagLog = DiagLog(File(app.filesDir, "diag.log"))
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
