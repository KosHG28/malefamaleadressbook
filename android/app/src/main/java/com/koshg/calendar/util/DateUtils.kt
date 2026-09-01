package com.koshg.calendar.util

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
val WEEKDAY_SHORT_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

fun monthYearLabel(date: LocalDate): String {
    val name = date.month.getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
        .replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }
    return "$name ${date.year}"
}

fun dayAgendaLabel(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
        .replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }
    val month = date.month.getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
    return "$dayOfWeek, ${date.dayOfMonth} $month"
}
