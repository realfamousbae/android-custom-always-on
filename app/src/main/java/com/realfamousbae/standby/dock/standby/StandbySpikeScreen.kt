package com.realfamousbae.standby.dock.standby

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Временный экран spike: часы и дата на чёрном. Тап — выход. На этапе 3 заменится пейджером виджетов. */
@Composable
fun StandbySpikeScreen(onExit: () -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onExit),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(time, color = Color(0xFFB0B0B0), fontSize = 160.sp, fontWeight = FontWeight.Light)
        Text(date, color = Color(0xFF707070), fontSize = 28.sp)
    }
}
