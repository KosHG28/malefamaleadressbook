package com.koshg.calendar.util

import com.koshg.calendar.data.PeriodEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Population-median cycle length, used only when there is no history at all to learn from. */
const val DEFAULT_CYCLE_LENGTH_DAYS = 29

/**
 * Luteal phase (ovulation → next period) length. 13 days tracks the population median more
 * closely than the commonly quoted "14" and is the default; pass a different value to
 * [computeCycleStats] / [cyclePhaseFor] for someone who knows their own norm.
 */
const val DEFAULT_LUTEAL_PHASE_DAYS = 13

/** EWMA smoothing factor for the cycle-length forecast — higher reacts faster, lower is steadier. */
private const val EWMA_ALPHA = 0.2

/**
 * How many days before predicted ovulation the LH (luteinizing hormone) surge peaks. The LH
 * surge is what triggers ovulation roughly 24-36h later, so one day before the predicted
 * ovulation date is the standard estimate absent any actual hormone-test data.
 */
const val LH_PEAK_OFFSET_DAYS = 1

/** How many fertile days are counted before predicted ovulation, before any dynamic margin. */
const val FERTILE_WINDOW_BEFORE_OVULATION_DAYS = 5

/** How many fertile days are counted after predicted ovulation, before any dynamic margin. */
const val FERTILE_WINDOW_AFTER_OVULATION_DAYS = 1

/** The dynamic fertile-window margin never widens the window by more than this many days per side. */
private const val MAX_MARGIN_DAYS = 4

/** A cycle is flagged irregular once the standard deviation of its recent lengths exceeds this. */
private const val IRREGULAR_SD_THRESHOLD_DAYS = 7.0

/** Only this many of the most recent cycle lengths feed the standard-deviation/irregularity check. */
private const val SD_LOOKBACK_CYCLES = 6

/** Assumed bleeding duration used only to shade calendar days — periods are logged as a single start date. */
const val ASSUMED_PERIOD_DURATION_DAYS = 5

/** Cycle-length deltas outside this range are treated as data-entry noise and excluded from the stats. */
private val PLAUSIBLE_CYCLE_LENGTH_RANGE = 15..60

/** Safety cap so a pathological/corrupt date never spins the boundary-walking loops forever. */
private const val MAX_CYCLE_WALK_STEPS = 2000

enum class CyclePhase(val label: String) {
    MENSTRUAL("Менструация"),
    FOLLICULAR("Фолликулярная"),
    LH_PEAK("Пик ЛГ"),
    OVULATORY("Овуляция"),
    LUTEAL("Лютеиновая")
}

data class CycleStats(
    val forecastCycleLengthDays: Int,
    val cycleHistoryCount: Int,
    val latestPeriodStart: LocalDate?,
    val currentCycleDay: Int?,
    val predictedNextPeriod: LocalDate?,
    val predictedNextPeriodEarliest: LocalDate?,
    val predictedNextPeriodLatest: LocalDate?,
    val predictedOvulation: LocalDate?,
    val predictedLhPeak: LocalDate?,
    val fertileWindowStart: LocalDate?,
    val fertileWindowEnd: LocalDate?,
    val cycleStandardDeviationDays: Double,
    val appliedMarginDays: Int,
    val isIrregular: Boolean
)

private val EMPTY_STATS = CycleStats(
    forecastCycleLengthDays = DEFAULT_CYCLE_LENGTH_DAYS,
    cycleHistoryCount = 0,
    latestPeriodStart = null,
    currentCycleDay = null,
    predictedNextPeriod = null,
    predictedNextPeriodEarliest = null,
    predictedNextPeriodLatest = null,
    predictedOvulation = null,
    predictedLhPeak = null,
    fertileWindowStart = null,
    fertileWindowEnd = null,
    cycleStandardDeviationDays = 0.0,
    appliedMarginDays = 0,
    isIrregular = false
)

private fun plausibleCycleLengths(sortedPeriodStarts: List<LocalDate>): List<Int> =
    sortedPeriodStarts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in PLAUSIBLE_CYCLE_LENGTH_RANGE }

/**
 * Forecasts the next cycle's length with an exponentially weighted moving average (alpha = 0.2),
 * which tracks genuine drift while damping a one-off outlier cycle far more gracefully than a
 * plain average would. An EWMA needs a "previous forecast" to recur from, which doesn't exist
 * yet with under 3 cycles of history — those cases fall back to a simple mean, and with no
 * history at all, to the population median ([DEFAULT_CYCLE_LENGTH_DAYS]).
 */
fun forecastCycleLength(sortedPeriodStarts: List<LocalDate>): Double {
    val lengths = plausibleCycleLengths(sortedPeriodStarts)
    return when {
        lengths.isEmpty() -> DEFAULT_CYCLE_LENGTH_DAYS.toDouble()
        lengths.size < 3 -> lengths.average()
        else -> {
            var forecast = lengths[0].toDouble()
            for (i in 1 until lengths.size) {
                forecast = EWMA_ALPHA * lengths[i] + (1 - EWMA_ALPHA) * forecast
            }
            forecast
        }
    }
}

/**
 * Sample standard deviation (n-1 denominator — this is an estimate from a limited sample, not a
 * full population) of the last [SD_LOOKBACK_CYCLES] plausible cycle lengths. Returns 0 with fewer
 * than two lengths, since spread is undefined for a single data point.
 */
fun cycleLengthStandardDeviation(sortedPeriodStarts: List<LocalDate>): Double {
    val lengths = plausibleCycleLengths(sortedPeriodStarts).takeLast(SD_LOOKBACK_CYCLES)
    if (lengths.size < 2) return 0.0
    val mean = lengths.average()
    val variance = lengths.sumOf { (it - mean) * (it - mean) } / (lengths.size - 1)
    return sqrt(variance)
}

/** `round(SD / 2)`, capped at [MAX_MARGIN_DAYS] and never negative. */
private fun marginDaysFor(standardDeviation: Double): Int =
    (standardDeviation / 2.0).roundToInt().coerceIn(0, MAX_MARGIN_DAYS)

fun computeCycleStats(
    periods: List<PeriodEntry>,
    today: LocalDate = LocalDate.now(),
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): CycleStats {
    val sortedDates = periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    if (sortedDates.isEmpty()) return EMPTY_STATS

    val latest = sortedDates.last()
    val forecastLength = forecastCycleLength(sortedDates)
    val roundedForecastLength = forecastLength.roundToInt()
    val plausibleLengths = plausibleCycleLengths(sortedDates)
    val standardDeviation = cycleLengthStandardDeviation(sortedDates)
    val marginDays = marginDaysFor(standardDeviation)
    val irregular = standardDeviation > IRREGULAR_SD_THRESHOLD_DAYS

    val nextPeriod = latest.plusDays(roundedForecastLength.toLong())
    val ovulation = nextPeriod.minusDays(lutealPhaseDays.toLong())
    val lhPeak = ovulation.minusDays(LH_PEAK_OFFSET_DAYS.toLong())
    val fertileStart = ovulation.minusDays((FERTILE_WINDOW_BEFORE_OVULATION_DAYS + marginDays).toLong())
    val fertileEnd = ovulation.plusDays((FERTILE_WINDOW_AFTER_OVULATION_DAYS + marginDays).toLong())

    return CycleStats(
        forecastCycleLengthDays = roundedForecastLength,
        cycleHistoryCount = plausibleLengths.size,
        latestPeriodStart = latest,
        currentCycleDay = ChronoUnit.DAYS.between(latest, today).toInt() + 1,
        predictedNextPeriod = nextPeriod,
        predictedNextPeriodEarliest = latest.plusDays((roundedForecastLength - marginDays).toLong()),
        predictedNextPeriodLatest = latest.plusDays((roundedForecastLength + marginDays).toLong()),
        predictedOvulation = ovulation,
        predictedLhPeak = lhPeak,
        fertileWindowStart = fertileStart,
        fertileWindowEnd = fertileEnd,
        cycleStandardDeviationDays = standardDeviation,
        appliedMarginDays = marginDays,
        isIrregular = irregular
    )
}

/**
 * Classifies [date] into a cycle phase by locating (or extrapolating, using the forecast cycle
 * length) the menstrual cycle it falls in — this works for any date, past or future, logged or
 * not, so a whole visible month grid — including days that spill into neighboring cycles — can
 * be colored consistently. Returns null only when there is no period history at all.
 *
 * [cycleStart] and [nextPeriodStart] are always walked together, one cycle-length step at a time,
 * in both directions — never re-derived independently — so a date several cycles before or after
 * the logged history still lands in the correct synthetic cycle instead of being measured against
 * a stale, too-distant boundary (which previously misclassified the days right before an
 * unlogged/extrapolated period start).
 */
fun cyclePhaseFor(
    date: LocalDate,
    periods: List<PeriodEntry>,
    marginDays: Int = 0,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): CyclePhase? {
    val sortedDates = periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    if (sortedDates.isEmpty()) return null

    val cycleLength = forecastCycleLength(sortedDates).roundToLong().coerceAtLeast(1)

    var cycleStart = sortedDates.first()
    var nextPeriodStart = sortedDates.getOrNull(1) ?: cycleStart.plusDays(cycleLength)

    var steps = 0
    while (date.isBefore(cycleStart) && steps < MAX_CYCLE_WALK_STEPS) {
        nextPeriodStart = cycleStart
        cycleStart = cycleStart.minusDays(cycleLength)
        steps++
    }

    steps = 0
    while (!date.isBefore(nextPeriodStart) && steps < MAX_CYCLE_WALK_STEPS) {
        cycleStart = nextPeriodStart
        nextPeriodStart = sortedDates.firstOrNull { it.isAfter(cycleStart) } ?: cycleStart.plusDays(cycleLength)
        steps++
    }

    val periodEnd = cycleStart.plusDays(ASSUMED_PERIOD_DURATION_DAYS.toLong())
    val ovulation = nextPeriodStart.minusDays(lutealPhaseDays.toLong())
    val lhPeak = ovulation.minusDays(LH_PEAK_OFFSET_DAYS.toLong())
    val fertileStart = ovulation.minusDays((FERTILE_WINDOW_BEFORE_OVULATION_DAYS + marginDays).toLong())
    val fertileEnd = ovulation.plusDays((FERTILE_WINDOW_AFTER_OVULATION_DAYS + marginDays).toLong())

    return when {
        date.isBefore(periodEnd) -> CyclePhase.MENSTRUAL
        date.isBefore(fertileStart) -> CyclePhase.FOLLICULAR
        date.isEqual(lhPeak) -> CyclePhase.LH_PEAK
        !date.isAfter(fertileEnd) -> CyclePhase.OVULATORY
        else -> CyclePhase.LUTEAL
    }
}
