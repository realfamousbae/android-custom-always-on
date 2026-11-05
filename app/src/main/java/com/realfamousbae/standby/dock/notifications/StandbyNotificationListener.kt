package com.realfamousbae.standby.dock.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.realfamousbae.standby.dock.core.notifications.BannerContent
import com.realfamousbae.standby.dock.core.notifications.BannerDedupe
import com.realfamousbae.standby.dock.core.notifications.LockscreenPrivacy
import com.realfamousbae.standby.dock.core.notifications.NotificationFilter
import com.realfamousbae.standby.dock.core.notifications.NotificationInfo
import com.realfamousbae.standby.dock.graph

/**
 * Слушатель уведомлений: пока режим показан, превращает новые уведомления в баннеры.
 * Решения (что показывать и сколько текста) — в :core ([NotificationFilter],
 * [LockscreenPrivacy]). Уже висящие уведомления при подключении не проигрываются.
 *
 * В журнал не пишутся ни тексты, ни имена приложений — только факт показа.
 */
class StandbyNotificationListener : NotificationListenerService() {

    private val dedupe = BannerDedupe()

    override fun onListenerConnected() {
        val privacy = when (systemAllowsPrivateNotifications(this)) {
            true -> "показывать"
            false -> "скрывать"
            null -> "не читается, скрываем"
        }
        graph.diagLog.log("Уведомления: слушатель подключён; содержимое на блокировке — $privacy")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        dedupe.forget(sbn.key)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val graph = graph
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
        // Запоминаем всегда, даже при скрытом режиме: иначе обновление старого уведомления
        // при показанном режиме выглядело бы как новое.
        val fresh = dedupe.accept(sbn.key, title, text)
        if (!graph.controller.visible.value || !fresh) return

        val ranking = Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
        val info = NotificationInfo(
            packageName = sbn.packageName,
            isOwnPackage = sbn.packageName == packageName,
            isOngoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            importance = ranking?.importance ?: NotificationFilter.IMPORTANCE_DEFAULT,
            matchesInterruptionFilter = ranking?.matchesInterruptionFilter() ?: true,
            ownQuietModeActive = graph.quietMode.active,
            userDndActive = graph.quietMode.userDndActive,
        )
        if (!NotificationFilter.shouldShow(info, graph.settings.value)) return

        val content = LockscreenPrivacy.decide(
            visibility = n.visibility,
            channelOverride = ranking?.lockscreenVisibilityOverride ?: LockscreenPrivacy.VISIBILITY_NO_OVERRIDE,
            systemAllowsPrivate = systemAllowsPrivateNotifications(this),
            hasPublicVersion = n.publicVersion != null,
        )
        val (shownTitle, shownText) = when (content) {
            BannerContent.Hidden -> return
            BannerContent.Full -> title to text
            BannerContent.AppOnly -> null to null
            BannerContent.PublicVersion -> n.publicVersion.extras.let {
                it.getCharSequence(Notification.EXTRA_TITLE)?.toString() to
                    it.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            }
        }

        val pm = packageManager
        val app = runCatching { pm.getApplicationInfo(sbn.packageName, 0) }.getOrNull()
        val icon = app?.let { runCatching { pm.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
        graph.notificationBus.post(
            BannerNotification(
                key = sbn.key,
                appName = app?.let { pm.getApplicationLabel(it).toString() } ?: sbn.packageName,
                icon = icon,
                title = shownTitle,
                text = shownText,
            ),
        )
        graph.diagLog.log("Уведомления: показан баннер")
    }

    companion object {
        fun component(context: Context) = ComponentName(context, StandbyNotificationListener::class.java)

        /**
         * Системная настройка «Показывать содержимое уведомлений на экране блокировки».
         * Ключ не публичный: нет значения или прошивка не даёт прочитать — null
         * (считаем «скрывать»). Что вышло на устройстве — видно в журнале при подключении.
         */
        fun systemAllowsPrivateNotifications(context: Context): Boolean? = try {
            Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications") != 0
        } catch (_: Exception) {
            null
        }
    }
}
