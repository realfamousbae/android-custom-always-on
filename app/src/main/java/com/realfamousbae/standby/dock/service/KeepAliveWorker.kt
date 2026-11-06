package com.realfamousbae.standby.dock.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Страховка на случай, если система убила службу и не перезапустила её (START_STICKY не
 * гарантирован). Режим работает и без зарядки, поэтому задача периодическая без условий:
 * раз в ~15 минут (система может сдвигать) поднимает службу. Если служба жива, повторный
 * запуск безвреден. Старт FGS из фона разрешён благодаря исключению из оптимизации батареи
 * (проверено в spike, docs/PLAN.md, этап 1). Планируется, только пока StandBy включён
 * ([RunState]); при выключенном тумблере запуск всё равно пропускается.
 */
class KeepAliveWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceStarter.startIfAllowed(applicationContext, reason = "worker")
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "standby_keep_alive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES).build()
            // UPDATE: заменяет задачу из прошлых версий (с условием «на зарядке»).
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** StandBy выключен — фоновые подъёмы не нужны. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
