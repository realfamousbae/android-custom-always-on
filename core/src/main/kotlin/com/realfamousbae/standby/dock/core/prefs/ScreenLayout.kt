package com.realfamousbae.standby.dock.core.prefs

/** Раскладка экрана режима. */
enum class ScreenLayout {
    /** Только часы на весь экран. */
    ClockOnly,

    /** Две равные карточки: слева часы, справа системный виджет. */
    ClockAndWidget;

    companion object {
        /**
         * Что реально показывать: если виджет не выбран или его приложение удалено,
         * вторую карточку показывать нечем — остаются только часы.
         */
        fun resolve(layout: ScreenLayout, widgetAvailable: Boolean): ScreenLayout =
            if (layout == ClockAndWidget && !widgetAvailable) ClockOnly else layout

        /** Разбор сохранённого имени; неизвестное значение — по умолчанию. */
        fun parse(name: String?): ScreenLayout = entries.firstOrNull { it.name == name } ?: ClockOnly
    }
}
