package com.realfamousbae.standby.dock.core.notifications

import com.realfamousbae.standby.dock.core.prefs.StandbySettings

/**
 * Свойства уведомления, нужные для решения «показывать ли баннер». Слой :app заполняет их из
 * StatusBarNotification и Ranking; значения важности и видимости — как в Android
 * (`NotificationManager.IMPORTANCE_*`, `Notification.VISIBILITY_*`).
 */
data class NotificationInfo(
    val packageName: String,
    val isOwnPackage: Boolean,
    /** Постоянное (ongoing) или от foreground-службы: музыка, загрузки, навигация. */
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val importance: Int,
    /** false — уведомление скрыто режимом «Не беспокоить». */
    val matchesInterruptionFilter: Boolean,
    /**
     * Активен наш собственный режим тишины (скрывает системные всплывающие уведомления).
     * Он перехватывает почти всё, поэтому сам по себе не должен гасить наши баннеры.
     */
    val ownQuietModeActive: Boolean = false,
    /** «Не беспокоить» был включён пользователем ещё до нашего режима. */
    val userDndActive: Boolean = false,
)

object NotificationFilter {

    /** `NotificationManager.IMPORTANCE_DEFAULT`: ниже — беззвучные уведомления. */
    const val IMPORTANCE_DEFAULT = 3

    fun shouldShow(info: NotificationInfo, settings: StandbySettings): Boolean = when {
        !settings.notificationBannersEnabled -> false
        info.isOwnPackage -> false
        info.isOngoing -> false
        info.isGroupSummary -> false
        info.importance < IMPORTANCE_DEFAULT -> false
        // Перехват засчитываем, только если его мог сделать пользовательский DND.
        !info.matchesInterruptionFilter && (!info.ownQuietModeActive || info.userDndActive) -> false
        info.packageName in settings.notificationBlockedPackages -> false
        else -> true
    }
}

/** Что из уведомления можно показать поверх экрана блокировки. */
enum class BannerContent {
    /** Иконка, приложение, заголовок и текст. */
    Full,

    /** Публичная версия уведомления, которую приложение подготовило для блокировки. */
    PublicVersion,

    /** Только иконка и имя приложения. */
    AppOnly,

    /** Не показывать вовсе. */
    Hidden,
}

/**
 * Решение «как в системе»: повторяет правила экрана блокировки Android.
 */
object LockscreenPrivacy {

    const val VISIBILITY_SECRET = -1
    const val VISIBILITY_PRIVATE = 0
    const val VISIBILITY_PUBLIC = 1

    /** `Ranking.getLockscreenVisibility()`, когда канал/пользователь ничего не переопределял. */
    const val VISIBILITY_NO_OVERRIDE = -1000

    /**
     * @param visibility поле `Notification.visibility` (по умолчанию PRIVATE).
     * @param channelOverride переопределение канала из Ranking или [VISIBILITY_NO_OVERRIDE].
     * @param systemAllowsPrivate системная настройка «показывать содержимое на экране
     *   блокировки»; null — прочитать не удалось, считаем, что содержимое скрыто.
     */
    fun decide(
        visibility: Int,
        channelOverride: Int,
        systemAllowsPrivate: Boolean?,
        hasPublicVersion: Boolean,
    ): BannerContent {
        // Переопределение канала может только ужесточить видимость, как в SystemUI.
        val effective = if (channelOverride == VISIBILITY_NO_OVERRIDE) visibility else minOf(visibility, channelOverride)
        return when {
            effective <= VISIBILITY_SECRET -> BannerContent.Hidden
            effective >= VISIBILITY_PUBLIC -> BannerContent.Full
            systemAllowsPrivate == true -> BannerContent.Full
            hasPublicVersion -> BannerContent.PublicVersion
            else -> BannerContent.AppOnly
        }
    }
}

/**
 * Отсекает повторные публикации того же уведомления без изменений (прогресс, обновления
 * медиа, перевыставление приложением). Хранит последние [capacity] ключей.
 */
class BannerDedupe(private val capacity: Int = 64) {

    private val seen = LinkedHashMap<String, Int>()

    /** true — показывать (новое уведомление или изменилось его содержимое). */
    fun accept(key: String, title: String?, text: String?): Boolean {
        val hash = (title.orEmpty() + '\u0000' + text.orEmpty()).hashCode()
        val previous = seen.remove(key)
        seen[key] = hash
        if (seen.size > capacity) seen.remove(seen.keys.first())
        return previous != hash
    }

    fun forget(key: String) {
        seen.remove(key)
    }
}
