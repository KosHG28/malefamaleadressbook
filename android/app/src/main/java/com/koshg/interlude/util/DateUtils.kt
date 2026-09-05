package com.koshg.interlude.util

import android.content.Context
import com.koshg.interlude.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every label here is built from the device's locale and from patterns that live in
 * strings.xml, never from a hardcoded language.
 *
 * The patterns have to be translatable rather than assembled in code, because languages disagree
 * on the order: Russian writes "3 сентября", English "September 3". The "LLLL" in the month-year
 * pattern is deliberate -- it asks for the standalone form of the month name, which in Russian is
 * the nominative "Сентябрь" rather than the genitive "Сентября" that "MMMM" yields. In English
 * the two forms are identical, so one pattern serves both.
 */
private fun Context.formatter(patternRes: Int): DateTimeFormatter =
    DateTimeFormatter.ofPattern(getString(patternRes), Locale.getDefault())

/** Short weekday names for the calendar's column headers, in the app's Monday-first order. */
fun weekdayShortNames(locale: Locale = Locale.getDefault()): List<String> =
    listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    ).map { day ->
        day.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase(locale) }
    }

fun Context.monthYearLabel(date: LocalDate): String =
    formatter(R.string.pattern_month_year).format(date)
        .replaceFirstChar { it.uppercase(Locale.getDefault()) }

fun Context.dayAgendaLabel(date: LocalDate): String =
    formatter(R.string.pattern_day_agenda).format(date)
        .replaceFirstChar { it.uppercase(Locale.getDefault()) }

/** "12 марта" / "March 12" — no year. */
fun Context.shortDateLabel(date: LocalDate): String =
    formatter(R.string.pattern_short_date).format(date)

/** "12 марта 2026" / "March 12, 2026". */
fun Context.fullDateLabel(date: LocalDate): String =
    formatter(R.string.pattern_full_date).format(date)

/** "сен 26" / "Sep 26" — short month plus two-digit year, for compact chart labels. */
fun Context.monthShortLabel(month: YearMonth): String =
    formatter(R.string.pattern_month_short).format(month.atDay(1))

fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
