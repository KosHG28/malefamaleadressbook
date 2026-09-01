package com.koshg.calendar.util

import com.koshg.calendar.data.PeriodEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Typical menstrual cycle length used until enough history exists to average one. */
const val DEFAULT_CYCLE_LENGTH_DAYS = 28

/** How long the luteal phase (ovulation → next period) runs — fairly constant biologically. */
const val LUTEAL_PHASE_DAYS = 14

/** How many fertile days are counted before predicted ovulation. */
const val FERTILE_WINDOW_BEFORE_OVULATION_DAYS = 5

/** How many fertile days are counted after predicted ovulation. */
const val FERTILE_WINDOW_AFTER_OVULATION_DAYS = 1

/** Assumed bleeding duration used only to shade calendar days — periods are logged as a single start date. */
const val ASSUMED_PERIOD_DURATION_DAYS = 5

/** Cycle-length deltas outside this range are treated as data-entry noise and excluded from the average. */
private val PLAUSIBLE_CYCLE_LENGTH_RANGE = 15..60

data class CycleStats(
    val averageCycleLengthDays: Int,
    val cycleHistoryCount: Int,
    val latestPeriodStart: LocalDate?,
    val currentCycleDay: Int?,
    val predictedNextPeriod: LocalDate?,
    val predictedOvulation: LocalDate?,
    val fertileWindowStart: LocalDate?,
    val fertileWindowEnd: LocalDate?
)

private val EMPTY_STATS = CycleStats(
    averageCycleLengthDays = DEFAULT_CYCLE_LENGTH_DAYS,
    cycleHistoryCount = 0,
    latestPeriodStart = null,
    currentCycleDay = null,
    predictedNextPeriod = null,
    predictedOvulation = null,
    fertileWindowStart = null,
    fertileWindowEnd = null
)

fun computeCycleStats(periods: List<PeriodEntry>, today: LocalDate = LocalDate.now()): CycleStats {
    val sortedDates = periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    if (sortedDates.isEmpty()) return EMPTY_STATS

    val latest = sortedDates.last()
    val cycleLengths = sortedDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in PLAUSIBLE_CYCLE_LENGTH_RANGE }
    val averageLength = if (cycleLengths.isNotEmpty()) {
        cycleLengths.average().roundToInt()
    } else {
        DEFAULT_CYCLE_LENGTH_DAYS
    }

    val nextPeriod = latest.plusDays(averageLength.toLong())
    val ovulation = nextPeriod.minusDays(LUTEAL_PHASE_DAYS.toLong())
    val fertileStart = ovulation.minusDays(FERTILE_WINDOW_BEFORE_OVULATION_DAYS.toLong())
    val fertileEnd = ovulation.plusDays(FERTILE_WINDOW_AFTER_OVULATION_DAYS.toLong())

    return CycleStats(
        averageCycleLengthDays = averageLength,
        cycleHistoryCount = cycleLengths.size,
        latestPeriodStart = latest,
        currentCycleDay = ChronoUnit.DAYS.between(latest, today).toInt() + 1,
        predictedNextPeriod = nextPeriod,
        predictedOvulation = ovulation,
        fertileWindowStart = fertileStart,
        fertileWindowEnd = fertileEnd
    )
}

/** True if [date] falls within [ASSUMED_PERIOD_DURATION_DAYS] of any logged period start. */
fun isLoggedPeriodDay(periods: List<PeriodEntry>, date: LocalDate): Boolean =
    periods.any { entry ->
        val start = entry.startDate.toLocalDateOrNull() ?: return@any false
        !date.isBefore(start) && date.isBefore(start.plusDays(ASSUMED_PERIOD_DURATION_DAYS.toLong()))
    }

/** True if [date] falls within the predicted (not yet logged) upcoming period range. */
fun isPredictedPeriodDay(stats: CycleStats, date: LocalDate): Boolean {
    val next = stats.predictedNextPeriod ?: return false
    return !date.isBefore(next) && date.isBefore(next.plusDays(ASSUMED_PERIOD_DURATION_DAYS.toLong()))
}

fun isFertileDay(stats: CycleStats, date: LocalDate): Boolean {
    val start = stats.fertileWindowStart ?: return false
    val end = stats.fertileWindowEnd ?: return false
    return !date.isBefore(start) && !date.isAfter(end)
}

fun isOvulationDay(stats: CycleStats, date: LocalDate): Boolean = stats.predictedOvulation == date
