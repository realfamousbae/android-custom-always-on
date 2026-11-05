package com.realfamousbae.standby.dock.standby

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Часы и дата. Кегль времени подбирается под ширину блока (не больше [maxTimeSize]),
 * поэтому один и тот же блок работает и на весь экран, и в половинной карточке.
 */
@Composable
fun ClockBlock(maxTimeSize: TextUnit, dateSize: TextUnit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            // Просыпаемся к началу следующей секунды, а не каждые 1000 мс «как получится».
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }

    val time = now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = time,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = StandbyColors.time,
                fontFamily = StandbyFonts.display,
                fontWeight = FontWeight.Light,
                fontFeatureSettings = StandbyFonts.TABULAR_NUMBERS,
                letterSpacing = (-0.03).em,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = maxTimeSize, stepSize = 4.sp),
        )
        BasicText(
            text = date,
            style = TextStyle(
                color = StandbyColors.secondary,
                fontFamily = StandbyFonts.text,
                fontWeight = FontWeight.Normal,
                fontSize = dateSize,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
