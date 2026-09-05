package com.koshg.interlude.util

import com.koshg.interlude.data.Initiator
import com.koshg.interlude.data.PeriodEntry
import com.koshg.interlude.data.ProposalEntry
import com.koshg.interlude.data.SexEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun period(date: LocalDate): PeriodEntry =
    PeriodEntry(id = "p-$date", startDate = date.toString(), notes = "")

private fun proposal(
    date: LocalDate,
    initiator: Initiator,
    accepted: Boolean,
    reason: String = "",
    answered: Boolean = true
): ProposalEntry =
    ProposalEntry(
        id = "pr-$date-${initiator.storageValue}",
        date = date.toString(),
        initiator = initiator.storageValue,
        accepted = accepted,
        answered = answered,
        declineReason = reason,
        notes = ""
    )

private fun sex(date: LocalDate, initiator: Initiator): SexEntry =
    SexEntry(id = "s-$date-${initiator.storageValue}", date = date.toString(), initiator = initiator.storageValue, myOrgasmCount = 0, partnerOrgasmCount = 0, notes = "")

class InsightsCalculatorTest {

    @Test
    fun `computeCorrelationInsights is empty with fewer than two periods`() {
        val insights = computeCorrelationInsights(
            periods = listOf(period(LocalDate.of(2026, 1, 1))),
            sexEntries = emptyList(),
            proposalEntries = emptyList()
        )
        assertTrue(insights.insights.isEmpty())
    }

    @Test
    fun `computeCorrelationInsights flags a phase where the partner clearly initiates more`() {
        // Two periods 29 days apart establishes a recurring 29-day cycle; the follicular window
        // for a cycle starting at `start` is [start+5, start+11) -- see CycleCalculatorTest for the
        // same math worked out day by day.
        val start = LocalDate.of(2026, 1, 1)
        val periods = listOf(period(start), period(start.plusDays(29)))

        // Four partner-initiated, accepted proposals landing in the follicular window of four
        // consecutive (mostly extrapolated) cycles.
        val follicularProposals = (0..3).map { cycle ->
            proposal(start.plusDays(29L * cycle + 7), Initiator.PARTNER, accepted = true)
        }
        // Four me-initiated sex entries in the luteal window (well after the fertile window closes)
        // of the same cycles, so the overall partner-share is diluted below the follicular phase's.
        val lutealSex = (0..3).map { cycle ->
            sex(start.plusDays(29L * cycle + 20), Initiator.ME)
        }

        val insights = computeCorrelationInsights(periods, lutealSex, follicularProposals)

        assertTrue(insights.insights.any { it.phase == CyclePhase.FOLLICULAR && "партнёра" in it.sentence })
    }

    @Test
    fun `computeCorrelationInsights flags a phase where fatigue declines cluster`() {
        val start = LocalDate.of(2026, 1, 1)
        val periods = listOf(period(start), period(start.plusDays(29)))

        val fatigueDeclines = listOf(
            proposal(start.plusDays(7), Initiator.ME, accepted = false, reason = "Усталость"),
            proposal(start.plusDays(36), Initiator.ME, accepted = false, reason = "Усталость после работы")
        )

        val insights = computeCorrelationInsights(periods, emptyList(), fatigueDeclines)

        assertTrue(insights.insights.any { it.phase == CyclePhase.FOLLICULAR && it.sentence.contains("усталост", ignoreCase = true) })
    }

    @Test
    fun `computeProactiveSuggestion fires for a long absence with no logged intimacy at all`() {
        val today = LocalDate.of(2026, 3, 1)
        val suggestion = computeProactiveSuggestion(
            sexEntries = emptyList(),
            masturbationEntries = emptyList(),
            proposalEntries = emptyList(),
            today = today
        )
        assertNotNull(suggestion)
    }

    @Test
    fun `computeProactiveSuggestion stays quiet with a recent entry and no fatigue pattern`() {
        val today = LocalDate.of(2026, 3, 10)
        val suggestion = computeProactiveSuggestion(
            sexEntries = listOf(sex(today.minusDays(1), Initiator.ME)),
            masturbationEntries = emptyList(),
            proposalEntries = emptyList(),
            today = today
        )
        assertNull(suggestion)
    }

    @Test
    fun `computeProactiveSuggestion fires on a recent fatigue-decline cluster even without a long absence`() {
        val today = LocalDate.of(2026, 3, 10)
        val suggestion = computeProactiveSuggestion(
            sexEntries = listOf(sex(today.minusDays(1), Initiator.ME)),
            masturbationEntries = emptyList(),
            proposalEntries = listOf(
                proposal(today.minusDays(5), Initiator.ME, accepted = false, reason = "Усталость"),
                proposal(today.minusDays(2), Initiator.ME, accepted = false, reason = "Устала после работы")
            ),
            today = today
        )
        assertNotNull(suggestion)
    }

    @Test
    fun `computeProposalOutcomes counts a sex entry with no proposal as accepted`() {
        val day = LocalDate.of(2026, 3, 1)
        val outcomes = computeProposalOutcomes(
            proposalEntries = emptyList(),
            sexEntries = listOf(sex(day, Initiator.ME))
        )
        assertEquals(1, outcomes.accepted)
        assertEquals(0, outcomes.declined)
        assertEquals(1, outcomes.fromSex)
    }

    @Test
    fun `computeProposalOutcomes counts several sex entries on one day once`() {
        val day = LocalDate.of(2026, 3, 1)
        val outcomes = computeProposalOutcomes(
            proposalEntries = emptyList(),
            sexEntries = listOf(sex(day, Initiator.ME), sex(day, Initiator.PARTNER))
        )
        assertEquals(1, outcomes.accepted)
        assertEquals(1, outcomes.answered)
    }

    @Test
    fun `computeProposalOutcomes leaves an explicit decline declined even with same-day sex`() {
        val day = LocalDate.of(2026, 3, 1)
        val outcomes = computeProposalOutcomes(
            proposalEntries = listOf(proposal(day, Initiator.ME, accepted = false)),
            sexEntries = listOf(sex(day, Initiator.ME))
        )
        assertEquals(0, outcomes.accepted)
        assertEquals(1, outcomes.declined)
        assertEquals(1, outcomes.answered)
        assertEquals(0, outcomes.fromSex)
    }

    @Test
    fun `computeProposalOutcomes resolves a pending proposal via same-day sex`() {
        val day = LocalDate.of(2026, 3, 1)
        val outcomes = computeProposalOutcomes(
            proposalEntries = listOf(proposal(day, Initiator.ME, accepted = false, answered = false)),
            sexEntries = listOf(sex(day, Initiator.ME))
        )
        assertEquals(1, outcomes.accepted)
        assertEquals(0, outcomes.pending)
        assertEquals(1, outcomes.fromSex)
    }

    @Test
    fun `computeProposalOutcomes keeps a pending proposal pending without sex that day`() {
        val day = LocalDate.of(2026, 3, 1)
        val outcomes = computeProposalOutcomes(
            proposalEntries = listOf(proposal(day, Initiator.ME, accepted = false, answered = false)),
            sexEntries = listOf(sex(day.plusDays(3), Initiator.ME))
        )
        assertEquals(1, outcomes.pending)
        assertEquals(1, outcomes.accepted)
        assertEquals(1, outcomes.answered)
    }

    @Test
    fun `computeProactiveSuggestion ignores old declines outside the fatigue lookback window`() {
        val today = LocalDate.of(2026, 3, 10)
        val suggestion = computeProactiveSuggestion(
            sexEntries = listOf(sex(today.minusDays(1), Initiator.ME)),
            masturbationEntries = emptyList(),
            proposalEntries = listOf(
                proposal(today.minusDays(200), Initiator.ME, accepted = false, reason = "Усталость"),
                proposal(today.minusDays(190), Initiator.ME, accepted = false, reason = "Усталость")
            ),
            today = today
        )
        assertNull(suggestion)
    }
}
