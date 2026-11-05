package com.realfamousbae.standby.dock.notifications

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Что показать в баннере. Текст уже урезан по правилам приватности экрана блокировки. */
data class BannerNotification(
    val key: String,
    val appName: String,
    val icon: ImageBitmap?,
    val title: String?,
    val text: String?,
)

/**
 * Канал от слушателя уведомлений к экрану режима. Небольшой буфер: если уведомления
 * сыплются пачкой, старые выбрасываются — показывать десяток баннеров подряд бессмысленно.
 */
class NotificationBus {
    private val _banners = MutableSharedFlow<BannerNotification>(
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val banners: SharedFlow<BannerNotification> = _banners.asSharedFlow()

    fun post(banner: BannerNotification) {
        _banners.tryEmit(banner)
    }
}
