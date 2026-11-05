package com.realfamousbae.standby.dock.standby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realfamousbae.standby.dock.notifications.BannerNotification
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

private const val HOLD_MS = 5_000L
private const val EXIT_MS = 380
private const val GAP_MS = 200L

/** Насколько баннер «откинут» назад в начале появления, градусы. */
private const val START_TILT_DEGREES = 75f

/**
 * Баннеры уведомлений сверху экрана, по одному. Появление — «вылет» с перспективой:
 * карточка поворачивается вокруг верхнего края из почти ребра к зрителю, съезжает вниз и
 * проявляется; через [HOLD_MS] уходит тем же путём. Тап по баннеру убирает его раньше
 * (режим при этом не закрывается).
 */
@Composable
fun NotificationBannerHost(banners: Flow<BannerNotification>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<BannerNotification?>(null) }
    val progress = remember { Animatable(0f) }
    val dismiss = remember { Channel<Unit>(Channel.CONFLATED) }

    LaunchedEffect(banners) {
        // collect обрабатывает по одному: пока баннер на экране, новые ждут в буфере шины.
        banners.collect { banner ->
            dismiss.tryReceive() // тап по прошлому баннеру не должен закрыть новый
            current = banner
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
            withTimeoutOrNull(HOLD_MS) { dismiss.receive() }
            progress.animateTo(0f, tween(EXIT_MS, easing = FastOutLinearInEasing))
            current = null
            delay(GAP_MS)
        }
    }

    val banner = current ?: return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        BannerCard(
            banner = banner,
            modifier = Modifier
                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progress.value
                    val hidden = 1f - p
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    // Значение уже в единицах плотности; меньше — сильнее перспектива.
                    cameraDistance = 7f
                    rotationX = START_TILT_DEGREES * hidden
                    translationY = -size.height * 0.5f * hidden
                    scaleX = 0.92f + 0.08f * p
                    scaleY = scaleX
                    alpha = p.coerceIn(0f, 1f)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dismiss.trySend(Unit) },
        )
    }
}

@Composable
private fun BannerCard(banner: BannerNotification, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(StandbyColors.banner)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        banner.icon?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.size(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                banner.appName,
                style = TextStyle(
                    color = StandbyColors.bannerText,
                    fontFamily = StandbyFonts.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Содержимое скрыто настройками экрана блокировки — только приложение.
            val title = banner.title ?: if (banner.text == null) "Новое уведомление" else null
            title?.let {
                BasicText(
                    it,
                    style = TextStyle(
                        color = StandbyColors.bannerTitle,
                        fontFamily = StandbyFonts.text,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            banner.text?.let {
                BasicText(
                    it,
                    style = TextStyle(
                        color = StandbyColors.bannerText,
                        fontFamily = StandbyFonts.text,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
