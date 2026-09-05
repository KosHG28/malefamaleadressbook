package com.koshg.interlude.util

import com.koshg.interlude.data.PeriodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun period(date: LocalDate): PeriodEntry =
    PeriodEntry(id = date.toString(), startDate = date.toString(), notes = "")

class CycleCalculatorTest {

    @Test
    fun `forecastCycleLength falls back to the population median with no history`() {
        assertEquals(DEFAULT_CYCLE_LENGTH_DAYS.toDouble(), forecastCycleLength(emptyList()), 0.0)
    }

    @Test
    fun `forecastCycleLength averages fewer than three cycles`() {
        val dates = listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 29), LocalDate.of(2026, 2, 27))
        // Two gaps: 28 and 29 days -- averaged, not EWMA'd, since there are fewer than 3 lengths.
        assertEquals(28.5, forecastCycleLength(dates), 0.0)
    }

    @Test
    fun `forecastCycleLength applies EWMA from the third cycle onward`() {
        val day0 = LocalDate.of(2026, 1, 1)
        val day1 = day0.plusDays(28)
        val day2 = day1.plusDays(30)
        val day3 = day2.plusDays(29)
        val dates = listOf(day0, day1, day2, day3)
        // lengths = [28, 30, 29]; forecast = 28 -> 0.2*30+0.8*28=28.4 -> 0.2*29+0.8*28.4=28.52
        assertEquals(28.52, forecastCycleLength(dates), 0.001)
    }

    @Test
    fun `forecastCycleLength ignores implausible gaps as data-entry noise`() {
        val day0 = LocalDate.of(2026, 1, 1)
        val day1 = day0.plusDays(5) // implausibly short, excluded
        val day2 = day1.plusDays(29)
        val dates = listOf(day0, day1, day2)
        assertEquals(29.0, forecastCycleLength(dates), 0.0)
    }

    @Test
    fun `cycleLengthStandardDeviation is zero with fewer than two lengths`() {
        assertEquals(0.0, cycleLengthStandardDeviation(emptyList()), 0.0)
        assertEquals(0.0, cycleLengthStandardDeviation(listOf(LocalDate.of(2026, 1, 1))), 0.0)
    }

    @Test
    fun `computeCycleStats reports no prediction with no history`() {
        val stats = computeCycleStats(emptyList())
        assertNull(stats.latestPeriodStart)
        assertNull(stats.predictedNextPeriod)
        assertEquals(DEFAULT_CYCLE_LENGTH_DAYS, stats.forecastCycleLengthDays)
        assertFalse(stats.isIrregular)
    }

    @Test
    fun `computeCycleStats predicts the next period from the latest start plus the forecast length`() {
        val day0 = LocalDate.of(2026, 1, 1)
        val day1 = day0.plusDays(29)
        val stats = computeCycleStats(listOf(period(day0), period(day1)), today = day1)
        assertEquals(day1, stats.latestPeriodStart)
        assertEquals(29, stats.forecastCycleLengthDays)
        assertEquals(day1.plusDays(29), stats.predictedNextPeriod)
    }

    @Test
    fun `computeCycleStats flags a wildly varying history as irregular`() {
        val start = LocalDate.of(2026, 1, 1)
        var current = start
        val lengths = listOf(21, 55, 24, 58, 20, 60)
        val dates = mutableListOf(current)
        lengths.forEach { length ->
            current = current.plusDays(length.toLong())
            dates += current
        }
        val stats = computeCycleStats(dates.map(::period), today = dates.last())
        assertTrue(stats.isIrregular)
    }

    @Test
    fun `computeCycleStats does not flag a tightly clustered history as irregular`() {
        val start = LocalDate.of(2026, 1, 1)
        var current = start
        val lengths = listOf(28, 29, 28, 30, 29)
        val dates = mutableListOf(current)
        lengths.forEach { length ->
            current = current.plusDays(length.toLong())
            dates += current
        }
        val stats = computeCycleStats(dates.map(::period), today = dates.last())
        assertFalse(stats.isIrregular)
    }

    @Test
    fun `cyclePhaseFor classifies a single-period cycle by section`() {
        // With one period and no history, the forecast falls back to the 28-day default and the
        // luteal length to its own 14-day default:
        //   cycleStart = Jan 1, nextPeriodStart = Jan 29 (Jan 1 + 28)
        //   periodEnd = Jan 6 (Jan 1 + 5), ovulation = Jan 15 (Jan 29 - 14)
        //   fertileStart = Jan 10, fertileEnd = Jan 16
        val periods = listOf(period(LocalDate.of(2026, 1, 1)))

        assertEquals(CyclePhase.MENSTRUAL, cyclePhaseFor(LocalDate.of(2026, 1, 1), periods))
        assertEquals(CyclePhase.MENSTRUAL, cyclePhaseFor(LocalDate.of(2026, 1, 5), periods))
        assertEquals(CyclePhase.FOLLICULAR, cyclePhaseFor(LocalDate.of(2026, 1, 6), periods))
        assertEquals(CyclePhase.FOLLICULAR, cyclePhaseFor(LocalDate.of(2026, 1, 9), periods))
        assertEquals(CyclePhase.OVULATORY, cyclePhaseFor(LocalDate.of(2026, 1, 10), periods))
        assertEquals(CyclePhase.OVULATORY, cyclePhaseFor(LocalDate.of(2026, 1, 15), periods))
        assertEquals(CyclePhase.OVULATORY, cyclePhaseFor(LocalDate.of(2026, 1, 16), periods))
        assertEquals(CyclePhase.LUTEAL, cyclePhaseFor(LocalDate.of(2026, 1, 17), periods))
        assertEquals(CyclePhase.LUTEAL, cyclePhaseFor(LocalDate.of(2026, 1, 28), periods))
    }

    @Test
    fun `ovulation is predicted exactly 14 days before the next period, as every calculator does`() {
        // The one rule the whole forecast rests on, and the one a user can check against any
        // other tool: ovulation = next period - DEFAULT_LUTEAL_PHASE_DAYS. Pinned for a logged
        // history (so the forecast, not the fallback, sets the cycle length) as well as for the
        // no-history case.
        val logged = listOf(
            period(LocalDate.of(2026, 1, 1)),
            period(LocalDate.of(2026, 1, 31)),
            period(LocalDate.of(2026, 3, 2))
        )
        val stats = computeCycleStats(logged, today = LocalDate.of(2026, 3, 10))
        val next = requireNotNull(stats.predictedNextPeriod)
        assertEquals(next.minusDays(DEFAULT_LUTEAL_PHASE_DAYS.toLong()), stats.predictedOvulation)

        val single = listOf(period(LocalDate.of(2026, 1, 1)))
        val singleStats = computeCycleStats(single, today = LocalDate.of(2026, 1, 10))
        assertEquals(LocalDate.of(2026, 1, 29), singleStats.predictedNextPeriod)
        assertEquals(LocalDate.of(2026, 1, 15), singleStats.predictedOvulation)
    }

    @Test
    fun `the predicted ovulation day always falls inside the ovulatory band`() {
        // The calendar paints a band and then glows one day inside it. If the two ever came apart,
        // the glow would land on a day coloured as some other phase.
        val periods = listOf(
            period(LocalDate.of(2026, 1, 1)),
            period(LocalDate.of(2026, 1, 26)),
            period(LocalDate.of(2026, 2, 27))
        )
        var date = LocalDate.of(2025, 12, 1)
        repeat(400) {
            val ovulation = ovulationDateFor(date, periods)
            if (ovulation != null) {
                assertEquals(
                    "ovulation day $ovulation is not inside the ovulatory band",
                    CyclePhase.OVULATORY,
                    cyclePhaseFor(ovulation, periods)
                )
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `the cycle day counts the period start as day one`() {
        val periods = listOf(period(LocalDate.of(2026, 8, 9)))
        assertEquals(1, computeCycleStats(periods, today = LocalDate.of(2026, 8, 9)).currentCycleDay)
        assertEquals(28, computeCycleStats(periods, today = LocalDate.of(2026, 9, 5)).currentCycleDay)
    }

    @Test
    fun `cyclePhaseFor returns null with no period history`() {
        assertNull(cyclePhaseFor(LocalDate.of(2026, 1, 1), emptyList()))
    }

    @Test
    fun `ovulationDateFor marks the single actual predicted ovulation day, not the day before it`() {
        // Same window as above: ovulation = Jan 15. UI marks a day as "the" ovulation day exactly
        // when it equals this -- so Jan 15 should match, and its neighbors should not.
        val periods = listOf(period(LocalDate.of(2026, 1, 1)))

        assertTrue(LocalDate.of(2026, 1, 15) == ovulationDateFor(LocalDate.of(2026, 1, 15), periods))
        assertFalse(LocalDate.of(2026, 1, 14) == ovulationDateFor(LocalDate.of(2026, 1, 14), periods))
        assertFalse(LocalDate.of(2026, 1, 16) == ovulationDateFor(LocalDate.of(2026, 1, 16), periods))
    }

    @Test
    fun `the prepared model answers exactly as the raw-list form does`() {
        // cyclePhaseFor now delegates to a CycleModel, and screens call the model directly to
        // avoid re-parsing the history per day. If the two ever drift, a calendar cell and a
        // statistic about the same date would disagree, so pin them together.
        val periods = listOf(
            period(LocalDate.of(2026, 1, 1)),
            period(LocalDate.of(2026, 1, 30)),
            period(LocalDate.of(2026, 2, 27))
        )
        val model = cycleModelOf(periods)
        var date = LocalDate.of(2025, 11, 1)
        repeat(240) {
            assertEquals(
                cyclePhaseFor(date, periods, marginDays = 1),
                model.phaseFor(date, marginDays = 1)
            )
            assertEquals(ovulationDateFor(date, periods), model.ovulationFor(date))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `ovulationDateFor returns null with no period history`() {
        assertNull(ovulationDateFor(LocalDate.of(2026, 1, 1), emptyList()))
    }
}
