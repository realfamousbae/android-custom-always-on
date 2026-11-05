package com.realfamousbae.standby.dock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.realfamousbae.standby.dock.R
import com.realfamousbae.standby.dock.settings.SettingsActivity

object Notifications {

    const val CHANNEL_SERVICE = "standby_service"
    const val ID_SERVICE = 1

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_SERVICE,
            "Фоновая служба",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Постоянное тихое уведомление: без него Android не даст службе работать"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun service(context: Context): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_standby)
            .setContentTitle("StandBy ждёт зарядку")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
