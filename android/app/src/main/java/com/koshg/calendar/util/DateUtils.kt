package com.koshg.calendar.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val RUSSIAN_LOCALE: Locale = Locale.forLanguageTag("ru")
val WEEKDAY_SHORT_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** Standalone-form ("LLLL") month name -- nominative case ("Сентябрь"), unlike the genitive
 *  ("Сентября") that java.time.Month.getDisplayName / the "MMMM" pattern give for Russian. */
private val NOMINATIVE_MONTH_FORMATTER = DateTimeFormatter.ofPattern("LLLL", RUSSIAN_LOCALE)

fun monthYearLabel(date: LocalDate): String {
    val name = NOMINATIVE_MONTH_FORMATTER.format(date).replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }
    return "$name ${date.year}"
}

fun dayAgendaLabel(date: LocalDate): String {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
        .replaceFirstChar { it.uppercase(RUSSIAN_LOCALE) }
    val month = date.month.getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
    return "$dayOfWeek, ${date.dayOfMonth} $month"
}

/** "12 марта" — genitive month name, no year. */
fun shortDateLabel(date: LocalDate): String {
    val month = date.month.getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE)
    return "${date.dayOfMonth} $month"
}

/** "12 марта 2026". */
fun fullDateLabel(date: LocalDate): String = "${shortDateLabel(date)} ${date.year}"

fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

/** "сен 26" — short month + two-digit year, for compact chart labels. */
fun monthShortLabel(month: YearMonth): String {
    val name = month.month.getDisplayName(TextStyle.SHORT, RUSSIAN_LOCALE)
    return "$name ${month.year % 100}"
}
