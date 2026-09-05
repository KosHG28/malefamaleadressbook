package com.koshg.interlude.util

import androidx.annotation.StringRes
import com.koshg.interlude.R
import com.koshg.interlude.data.PeriodEntry
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

enum class CyclePhase(@StringRes val labelRes: Int) {
    MENSTRUAL(R.string.phase_menstrual),
    FOLLICULAR(R.string.phase_follicular),
    OVULATORY(R.string.phase_ovulatory),
    LUTEAL(R.string.phase_luteal)
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
        fertileWindowStart = fertileStart,
        fertileWindowEnd = fertileEnd,
        cycleStandardDeviationDays = standardDeviation,
        appliedMarginDays = marginDays,
        isIrregular = irregular
    )
}

private data class CycleWindow(val cycleStart: LocalDate, val nextPeriodStart: LocalDate)

/**
 * Everything the per-day questions need out of [PeriodEntry], worked out once.
 *
 * [cyclePhaseFor] and friends each take the raw list, which means every single call re-parses
 * every logged date out of its string, re-sorts them, and recomputes the EWMA forecast before it
 * can answer anything. One call is nothing; a screen is not. The month grid asks 42 times, and the
 * year overview asks about 360 times for one page -- so paging through years redid that work
 * thousands of times per second on the composition thread, which is enough to lock the UI up
 * rather than merely slow it down.
 *
 * Build one of these where the dates come from (a [remember] keyed on the period list) and ask it
 * instead: the parsing and forecasting happen once, and each day costs a lookup and a short walk.
 */
class CycleModel internal constructor(
    internal val sortedStarts: List<LocalDate>,
    internal val cycleLength: Long,
    /** Logged end dates by their cycle's start, so [periodEndFor] needs no scan per day. */
    internal val loggedEnds: Map<LocalDate, LocalDate>
) {
    internal val hasHistory: Boolean get() = sortedStarts.isNotEmpty()
}

fun cycleModelOf(periods: List<PeriodEntry>): CycleModel {
    val sortedStarts = periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    val loggedEnds = buildMap {
        periods.forEach { entry ->
            val start = entry.startDate.toLocalDateOrNull() ?: return@forEach
            val end = entry.endDate?.toLocalDateOrNull() ?: return@forEach
            if (!end.isBefore(start)) put(start, end)
        }
    }
    return CycleModel(
        sortedStarts = sortedStarts,
        cycleLength = forecastCycleLength(sortedStarts).roundToLong().coerceAtLeast(1),
        loggedEnds = loggedEnds
    )
}

/**
 * Locates (or extrapolates, using the forecast cycle length) the menstrual cycle [date] falls
 * in — this works for any date, past or future, logged or not, so a whole visible month grid —
 * including days that spill into neighboring cycles — can be colored consistently. Returns null
 * only when there is no period history at all.
 *
 * [CycleWindow.cycleStart] and [CycleWindow.nextPeriodStart] are always walked together, one
 * cycle-length step at a time, in both directions — never re-derived independently — so a date
 * several cycles before or after the logged history still lands in the correct synthetic cycle
 * instead of being measured against a stale, too-distant boundary (which previously misclassified
 * the days right before an unlogged/extrapolated period start).
 */
private fun resolveCycleWindow(date: LocalDate, model: CycleModel): CycleWindow? {
    val sortedDates = model.sortedStarts
    if (sortedDates.isEmpty()) return null

    val cycleLength = model.cycleLength

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

    return CycleWindow(cycleStart, nextPeriodStart)
}

/**
 * The exclusive end of the menstrual-phase block starting at [cycleStart] -- the day after the
 * logged [PeriodEntry.endDate] for that specific period when one was recorded, so a cycle with an
 * actual end date colors exactly as long as it really ran rather than the fixed assumption. Falls
 * back to [ASSUMED_PERIOD_DURATION_DAYS] for predicted/future cycles and any period logged before
 * this field existed.
 */
private fun periodEndFor(cycleStart: LocalDate, model: CycleModel): LocalDate {
    val loggedEnd = model.loggedEnds[cycleStart]
    return loggedEnd?.plusDays(1) ?: cycleStart.plusDays(ASSUMED_PERIOD_DURATION_DAYS.toLong())
}

/** Classifies [date] into a cycle phase. See [resolveCycleWindow] for how the enclosing cycle is found. */
fun cyclePhaseFor(
    date: LocalDate,
    periods: List<PeriodEntry>,
    marginDays: Int = 0,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): CyclePhase? = cycleModelOf(periods).phaseFor(date, marginDays, lutealPhaseDays)

/** [cyclePhaseFor] against an already-built [CycleModel] -- what a whole grid or mosaic should
 *  call, so the parsing and forecasting happen once for the screen rather than once per day. */
fun CycleModel.phaseFor(
    date: LocalDate,
    marginDays: Int = 0,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): CyclePhase? {
    val window = resolveCycleWindow(date, this) ?: return null

    val periodEnd = periodEndFor(window.cycleStart, this)
    val ovulation = window.nextPeriodStart.minusDays(lutealPhaseDays.toLong())
    val fertileStart = ovulation.minusDays((FERTILE_WINDOW_BEFORE_OVULATION_DAYS + marginDays).toLong())
    val fertileEnd = ovulation.plusDays((FERTILE_WINDOW_AFTER_OVULATION_DAYS + marginDays).toLong())

    return when {
        date.isBefore(periodEnd) -> CyclePhase.MENSTRUAL
        date.isBefore(fertileStart) -> CyclePhase.FOLLICULAR
        !date.isAfter(fertileEnd) -> CyclePhase.OVULATORY
        else -> CyclePhase.LUTEAL
    }
}

/**
 * The predicted ovulation date for the cycle window [date] falls in -- same window-resolution as
 * [cyclePhaseFor], so a specific day within the (multi-day) OVULATORY phase can be checked against
 * it to render a "this is the actual predicted ovulation day" marker, instead of carving out a
 * whole separate phase color for it (which previously highlighted the day *before* ovulation --
 * the LH-peak estimate -- leaving actual ovulation day visually indistinguishable from the rest of
 * the fertile window).
 */
fun ovulationDateFor(
    date: LocalDate,
    periods: List<PeriodEntry>,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): LocalDate? = cycleModelOf(periods).ovulationFor(date, lutealPhaseDays)

/** [ovulationDateFor] against an already-built [CycleModel]. */
fun CycleModel.ovulationFor(
    date: LocalDate,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): LocalDate? {
    val window = resolveCycleWindow(date, this) ?: return null
    return window.nextPeriodStart.minusDays(lutealPhaseDays.toLong())
}

/**
 * Same classification as [cyclePhaseFor], but also returns how far [date] sits through that
 * phase (0f = the phase's first day, 1f = its last) — used to blend an "adaptive" accent color
 * smoothly between consecutive phases instead of jumping at each boundary. Returns null under
 * the same condition as [cyclePhaseFor].
 */
fun cyclePhaseProgressFor(
    date: LocalDate,
    periods: List<PeriodEntry>,
    marginDays: Int = 0,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): Pair<CyclePhase, Float>? {
    val model = cycleModelOf(periods)
    val window = resolveCycleWindow(date, model) ?: return null

    val periodEnd = periodEndFor(window.cycleStart, model)
    val ovulation = window.nextPeriodStart.minusDays(lutealPhaseDays.toLong())
    val fertileStart = ovulation.minusDays((FERTILE_WINDOW_BEFORE_OVULATION_DAYS + marginDays).toLong())
    val fertileEnd = ovulation.plusDays((FERTILE_WINDOW_AFTER_OVULATION_DAYS + marginDays).toLong())

    fun progressWithin(start: LocalDate, endExclusive: LocalDate): Float {
        val span = ChronoUnit.DAYS.between(start, endExclusive)
        if (span <= 0) return 0f
        return (ChronoUnit.DAYS.between(start, date).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    return when {
        date.isBefore(periodEnd) -> CyclePhase.MENSTRUAL to progressWithin(window.cycleStart, periodEnd)
        date.isBefore(fertileStart) -> CyclePhase.FOLLICULAR to progressWithin(periodEnd, fertileStart)
        !date.isAfter(fertileEnd) -> CyclePhase.OVULATORY to progressWithin(fertileStart, fertileEnd.plusDays(1))
        else -> CyclePhase.LUTEAL to progressWithin(fertileEnd.plusDays(1), window.nextPeriodStart)
    }
}
