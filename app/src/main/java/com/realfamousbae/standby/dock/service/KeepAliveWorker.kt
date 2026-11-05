package com.realfamousbae.standby.dock.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Страховка на случай, если Samsung убил службу. ACTION_POWER_CONNECTED нельзя ловить
 * receiver'ом из манифеста, поэтому используем периодическую задачу с условием «на зарядке»:
 * пока телефон не заряжается, просроченная задача ждёт, а при подключении зарядки система
 * запускает её вскоре после выполнения условия. Если служба жива, повторный запуск безвреден.
 */
class KeepAliveWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ServiceStarter.start(applicationContext, reason = "worker")
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "standby_keep_alive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
