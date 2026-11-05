package com.realfamousbae.standby.dock.core.notifications

import com.realfamousbae.standby.dock.core.notifications.LockscreenPrivacy.VISIBILITY_NO_OVERRIDE
import com.realfamousbae.standby.dock.core.notifications.LockscreenPrivacy.VISIBILITY_PRIVATE
import com.realfamousbae.standby.dock.core.notifications.LockscreenPrivacy.VISIBILITY_PUBLIC
import com.realfamousbae.standby.dock.core.notifications.LockscreenPrivacy.VISIBILITY_SECRET
import com.realfamousbae.standby.dock.core.prefs.StandbySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {

    private val message = NotificationInfo(
        packageName = "app.messenger",
        isOwnPackage = false,
        isOngoing = false,
        isGroupSummary = false,
        importance = 4,
        matchesInterruptionFilter = true,
    )
    private val settings = StandbySettings()

    @Test
    fun `обычное сообщение показывается`() {
        assertTrue(NotificationFilter.shouldShow(message, settings))
        assertTrue(NotificationFilter.shouldShow(message.copy(importance = 3), settings))
    }

    @Test
    fun `беззвучные, постоянные, сводки, свои и под DND не показываются`() {
        assertFalse(NotificationFilter.shouldShow(message.copy(importance = 2), settings))
        assertFalse(NotificationFilter.shouldShow(message.copy(isOngoing = true), settings))
        assertFalse(NotificationFilter.shouldShow(message.copy(isGroupSummary = true), settings))
        assertFalse(NotificationFilter.shouldShow(message.copy(isOwnPackage = true), settings))
        assertFalse(NotificationFilter.shouldShow(message.copy(matchesInterruptionFilter = false), settings))
    }

    @Test
    fun `чёрный список и выключатель баннеров`() {
        val blocked = settings.copy(notificationBlockedPackages = setOf("app.messenger"))
        assertFalse(NotificationFilter.shouldShow(message, blocked))
        assertTrue(NotificationFilter.shouldShow(message.copy(packageName = "app.other"), blocked))
        assertFalse(NotificationFilter.shouldShow(message, settings.copy(notificationBannersEnabled = false)))
    }

    @Test
    fun `приватность как в системе`() {
        fun d(v: Int, o: Int = VISIBILITY_NO_OVERRIDE, allow: Boolean? = true, pub: Boolean = false) =
            LockscreenPrivacy.decide(v, o, allow, pub)

        assertEquals(BannerContent.Full, d(VISIBILITY_PRIVATE, allow = true))
        assertEquals(BannerContent.AppOnly, d(VISIBILITY_PRIVATE, allow = false))
        assertEquals(BannerContent.PublicVersion, d(VISIBILITY_PRIVATE, allow = false, pub = true))
        // Настройку прочитать не удалось — осторожно.
        assertEquals(BannerContent.AppOnly, d(VISIBILITY_PRIVATE, allow = null))
        assertEquals(BannerContent.Full, d(VISIBILITY_PUBLIC, allow = false))
        assertEquals(BannerContent.Hidden, d(VISIBILITY_SECRET))
        // Канал ужесточил видимость.
        assertEquals(BannerContent.Hidden, d(VISIBILITY_PUBLIC, o = VISIBILITY_SECRET))
        assertEquals(BannerContent.AppOnly, d(VISIBILITY_PUBLIC, o = VISIBILITY_PRIVATE, allow = false))
    }

    @Test
    fun `повторная публикация без изменений не показывается`() {
        val dedupe = BannerDedupe(capacity = 2)
        assertTrue(dedupe.accept("k1", "Аня", "Привет"))
        assertFalse(dedupe.accept("k1", "Аня", "Привет"))
        assertTrue(dedupe.accept("k1", "Аня", "Ты где?"))
        dedupe.forget("k1")
        assertTrue(dedupe.accept("k1", "Аня", "Ты где?"))
        // Старые ключи вытесняются.
        dedupe.accept("k2", "a", "b")
        dedupe.accept("k3", "a", "b")
        assertTrue(dedupe.accept("k1", "Аня", "Ты где?"))
    }
}
