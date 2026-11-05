package com.realfamousbae.standby.dock.standby

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.appwidget.AppWidgetHostView
import com.realfamousbae.standby.dock.core.prefs.ScreenLayout
import com.realfamousbae.standby.dock.notifications.BannerNotification
import com.realfamousbae.standby.dock.widgets.StandbyWidgetHost
import kotlinx.coroutines.flow.Flow

private val CardShape = RoundedCornerShape(36.dp)

/**
 * Экран режима: часы на весь экран или две равные карточки «часы | системный виджет»,
 * поверх — баннеры уведомлений. Тап по часам закрывает режим; касания виджета получает
 * сам виджет.
 */
@Composable
fun StandbyScreen(
    layout: ScreenLayout,
    widgetId: Int?,
    widgetHost: StandbyWidgetHost,
    banners: Flow<BannerNotification>,
    onExit: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(StandbyColors.background)
            .clickable(interactionSource = noRipple, indication = null, onClick = onExit),
    ) {
        when {
            layout == ScreenLayout.ClockAndWidget && widgetId != null -> Row(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Card(Modifier.weight(1f)) {
                    ClockBlock(maxTimeSize = 128.sp, dateSize = 22.sp, modifier = Modifier.padding(24.dp))
                }
                // Пустой обработчик: касание, которое виджет не забрал, не должно закрыть режим.
                Card(Modifier.weight(1f).clickable(interactionSource = noRipple, indication = null) {}) {
                    key(widgetId) { WidgetView(widgetHost, widgetId) }
                }
            }
            else -> ClockBlock(
                maxTimeSize = 200.sp,
                dateSize = 28.sp,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
            )
        }
        NotificationBannerHost(banners, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(CardShape)
            .background(StandbyColors.card)
            .border(1.dp, StandbyColors.cardBorder, CardShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Системный виджет; о реальном размере карточки сообщаем виджету, чтобы он подобрал раскладку. */
@Composable
private fun WidgetView(widgetHost: StandbyWidgetHost, widgetId: Int) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val hostView: AppWidgetHostView? = remember { widgetHost.createView(context, widgetId) }
    AndroidView(
        factory = { hostView ?: View(it) },
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .onSizeChanged { size ->
                val view = hostView ?: return@onSizeChanged
                with(density) { widgetHost.updateSize(view, size.width.toDp().value, size.height.toDp().value) }
            },
    )
}
