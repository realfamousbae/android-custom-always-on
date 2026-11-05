package com.realfamousbae.standby.dock.standby

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.realfamousbae.standby.dock.R

/**
 * Шрифты экрана режима. Фирменный шрифт One UI проприетарный, поэтому — Inter (SIL OFL 1.1,
 * лицензия в `licenses/Inter-OFL.txt`): современный гротеск, близкий по пропорциям.
 * Inter Display — оптическая версия для крупного кегля, ей набраны часы.
 */
object StandbyFonts {
    val display = FontFamily(Font(R.font.inter_display_light, FontWeight.Light))
    val text = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
    )

    /** Цифры одной ширины: время не «прыгает» при смене минут. */
    const val TABULAR_NUMBERS = "tnum"
}

/** Палитра режима: приглушённые серые на чистом чёрном (OLED). */
object StandbyColors {
    val background = Color.Black
    val time = Color(0xFFB8B8B8)
    val secondary = Color(0xFF777777)
    val card = Color(0xFF0D0D0D)
    val cardBorder = Color(0xFF242424)
    val banner = Color(0xFF1C1C1E)
    val bannerTitle = Color(0xFFE6E6E6)
    val bannerText = Color(0xFF9A9A9A)
}
