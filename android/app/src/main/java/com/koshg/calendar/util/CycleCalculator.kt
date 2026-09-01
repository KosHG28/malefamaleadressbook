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

/** Safety cap so a pathological/corrupt date never spins the boundary-walking loops forever. */
private const val MAX_CYCLE_WALK_STEPS = 2000

enum class CyclePhase(val label: String) {
    MENSTRUAL("Менструация"),
    FOLLICULAR("Фолликулярная"),
    OVULATORY("Овуляция"),
    LUTEAL("Лютеиновая")
}

/** Fallback uncertainty band (± days) around the average when there isn't enough history to measure one. */
private const val FALLBACK_PREDICTION_SPREAD_DAYS = 2

data class CycleStats(
    val averageCycleLengthDays: Int,
    val cycleHistoryCount: Int,
    val latestPeriodStart: LocalDate?,
    val currentCycleDay: Int?,
    val predictedNextPeriod: LocalDate?,
    val predictedNextPeriodEarliest: LocalDate?,
    val predictedNextPeriodLatest: LocalDate?,
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
    predictedNextPeriodEarliest = null,
    predictedNextPeriodLatest = null,
    predictedOvulation = null,
    fertileWindowStart = null,
    fertileWindowEnd = null
)

/** Average gap between consecutive logged periods, or [DEFAULT_CYCLE_LENGTH_DAYS] with fewer than two. */
fun averageCycleLength(sortedPeriodStarts: List<LocalDate>): Int {
    val lengths = sortedPeriodStarts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in PLAUSIBLE_CYCLE_LENGTH_RANGE }
    return if (lengths.isNotEmpty()) lengths.average().roundToInt() else DEFAULT_CYCLE_LENGTH_DAYS
}

fun computeCycleStats(periods: List<PeriodEntry>, today: LocalDate = LocalDate.now()): CycleStats {
    val sortedDates = periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    if (sortedDates.isEmpty()) return EMPTY_STATS

    val latest = sortedDates.last()
    val averageLength = averageCycleLength(sortedDates)
    val plausibleLengths = sortedDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in PLAUSIBLE_CYCLE_LENGTH_RANGE }

    val nextPeriod = latest.plusDays(averageLength.toLong())
    val ovulation = nextPeriod.minusDays(LUTEAL_PHASE_DAYS.toLong())
    val fertileStart = ovulation.minusDays(FERTILE_WINDOW_BEFORE_OVULATION_DAYS.toLong())
    val fertileEnd = ovulation.plusDays(FERTILE_WINDOW_AFTER_OVULATION_DAYS.toLong())

    val earliestLength = plausibleLengths.minOrNull() ?: (averageLength - FALLBACK_PREDICTION_SPREAD_DAYS)
    val latestLength = plausibleLengths.maxOrNull() ?: (averageLength + FALLBACK_PREDICTION_SPREAD_DAYS)

    return CycleStats(
        averageCycleLengthDays = averageLength,
        cycleHistoryCount = plausibleLengths.size,
        latestPeriodStart = latest,
        currentCycleDay = ChronoUnit.DAYS.between(latest, today).toInt() + 1,
        predictedNextPeriod = nextPeriod,
        predictedNextPeriodEarliest = latest.plusDays(earliestLength.toLong()),
        predictedNextPeriodLatest = latest.plusDays(latestLength.toLong()),
        predictedOvulation = ovulation,
        fertileWindowStart = fertileStart,
        fertileWindowEnd = fertileEnd
    )
}

/**
 * Classifies [date] into a cycle phase by locating (or extrapolating, using the average cycle
 * length) the menstrual cycle it falls in — this works for any date, past or future, logged or
 * not, so a whole visible month grid — including days that spill into neighboring cycles — can
 * be colored consistently. Returns null only when there is no period history at all.
 */
fun cyclePhaseFor(date: LocalDate, periods: List<PeriodEntry>): CyclePhase? {
    val sortedDates = periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    if (sortedDates.isEmpty()) return null

    val avgLength = averageCycleLength(sortedDates).toLong()

    var cycleStart = sortedDates.lastOrNull { !it.isAfter(date) }
    if (cycleStart == null) {
        // date is before every logged period — extrapolate backward from the earliest one.
        var candidate = sortedDates.first()
        var steps = 0
        while (candidate.isAfter(date) && steps < MAX_CYCLE_WALK_STEPS) {
            candidate = candidate.minusDays(avgLength)
            steps++
        }
        cycleStart = candidate
    }

    var nextPeriodStart = sortedDates.firstOrNull { it.isAfter(cycleStart) } ?: cycleStart.plusDays(avgLength)
    var steps = 0
    while (!date.isBefore(nextPeriodStart) && steps < MAX_CYCLE_WALK_STEPS) {
        cycleStart = nextPeriodStart
        nextPeriodStart = sortedDates.firstOrNull { it.isAfter(cycleStart) } ?: cycleStart.plusDays(avgLength)
        steps++
    }

    val periodEnd = cycleStart.plusDays(ASSUMED_PERIOD_DURATION_DAYS.toLong())
    val ovulation = nextPeriodStart.minusDays(LUTEAL_PHASE_DAYS.toLong())
    val fertileStart = ovulation.minusDays(FERTILE_WINDOW_BEFORE_OVULATION_DAYS.toLong())
    val fertileEnd = ovulation.plusDays(FERTILE_WINDOW_AFTER_OVULATION_DAYS.toLong())

    return when {
        date.isBefore(periodEnd) -> CyclePhase.MENSTRUAL
        date.isBefore(fertileStart) -> CyclePhase.FOLLICULAR
        !date.isAfter(fertileEnd) -> CyclePhase.OVULATORY
        else -> CyclePhase.LUTEAL
    }
}
