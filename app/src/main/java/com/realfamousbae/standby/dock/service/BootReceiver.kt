package com.realfamousbae.standby.dock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.realfamousbae.standby.dock.graph
import kotlinx.coroutines.launch

/** Поднимает службу после перезагрузки и после обновления APK, если StandBy включён. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Проверка выключателя асинхронная: держим receiver живым до её окончания,
                // чтобы запуск FGS остался в окне, разрешённом для BOOT_COMPLETED.
                val pending = goAsync()
                context.graph.appScope.launch {
                    try {
                        ServiceStarter.startIfAllowed(context, reason = intent.action!!.substringAfterLast('.'))
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
