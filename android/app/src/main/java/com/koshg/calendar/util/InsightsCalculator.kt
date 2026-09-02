package com.koshg.calendar.util

import com.koshg.calendar.data.Initiator
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Below this many samples in a phase bucket, a rate/skew is too noisy to state as a pattern. */
private const val MIN_PHASE_SAMPLES = 4

/** How much a phase's partner-initiated share must beat the overall average to be worth calling out. */
private const val INITIATOR_SKEW_THRESHOLD = 0.2f

/** How many "усталость"-flavored declines in one phase before it reads as a pattern, not noise. */
private const val FATIGUE_DECLINE_MIN_IN_PHASE = 2

private data class PhaseTally(
    var meInitiated: Int = 0,
    var partnerInitiated: Int = 0,
    var accepted: Int = 0,
    var declined: Int = 0,
    var fatigueDeclines: Int = 0
) {
    val totalInitiations get() = meInitiated + partnerInitiated
    val totalProposals get() = accepted + declined
}

data class CorrelationInsights(val sentences: List<String>)

/**
 * Looks for simple, sample-size-gated patterns between cycle phase and intimacy initiative/
 * acceptance -- everything here is plain aggregation over already-logged entries, no inference
 * beyond counting, so results stay predictable and explainable.
 */
fun computeCorrelationInsights(
    periods: List<PeriodEntry>,
    sexEntries: List<SexEntry>,
    proposalEntries: List<ProposalEntry>,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): CorrelationInsights {
    if (periods.size < 2) return CorrelationInsights(emptyList())

    fun phaseOf(dateStr: String): CyclePhase? =
        dateStr.toLocalDateOrNull()?.let { cyclePhaseFor(it, periods, lutealPhaseDays = lutealPhaseDays) }

    val tallies = CyclePhase.entries.associateWith { PhaseTally() }

    sexEntries.forEach { entry ->
        val tally = phaseOf(entry.date)?.let { tallies[it] } ?: return@forEach
        when (Initiator.fromStorage(entry.initiator)) {
            Initiator.ME -> tally.meInitiated++
            Initiator.PARTNER -> tally.partnerInitiated++
        }
    }

    proposalEntries.forEach { entry ->
        val tally = phaseOf(entry.date)?.let { tallies[it] } ?: return@forEach
        when (Initiator.fromStorage(entry.initiator)) {
            Initiator.ME -> tally.meInitiated++
            Initiator.PARTNER -> tally.partnerInitiated++
        }
        if (entry.accepted) {
            tally.accepted++
        } else {
            tally.declined++
            if (entry.declineReason.contains("устал", ignoreCase = true)) tally.fatigueDeclines++
        }
    }

    val sentences = mutableListOf<String>()

    val overallPartnerShare = tallies.values.sumOf { it.partnerInitiated }
        .toFloat()
        .let { partnerTotal -> partnerTotal / tallies.values.sumOf { it.totalInitiations }.coerceAtLeast(1) }

    tallies.entries
        .filter { it.value.totalInitiations >= MIN_PHASE_SAMPLES }
        .maxByOrNull { it.value.partnerInitiated.toFloat() / it.value.totalInitiations }
        ?.let { (phase, tally) ->
            val share = tally.partnerInitiated.toFloat() / tally.totalInitiations
            if (share - overallPartnerShare >= INITIATOR_SKEW_THRESHOLD) {
                sentences += "В фазу «${phase.label}» инициатива чаще исходит от партнёра " +
                    "(${(share * 100).roundToInt()}% случаев)."
            }
        }

    val withProposals = tallies.entries.filter { it.value.totalProposals >= MIN_PHASE_SAMPLES }
    withProposals.maxByOrNull { it.value.accepted.toFloat() / it.value.totalProposals }?.let { (phase, tally) ->
        val rate = (tally.accepted.toFloat() / tally.totalProposals * 100).roundToInt()
        sentences += "В фазу «${phase.label}» процент принятых предложений максимален ($rate%)."
    }
    withProposals.minByOrNull { it.value.accepted.toFloat() / it.value.totalProposals }?.let { (phase, tally) ->
        val declineRate = ((1f - tally.accepted.toFloat() / tally.totalProposals) * 100).roundToInt()
        if (declineRate > 0) {
            sentences += "В фазу «${phase.label}» отказов больше всего ($declineRate%)."
        }
    }

    tallies.entries
        .filter { it.value.fatigueDeclines >= FATIGUE_DECLINE_MIN_IN_PHASE }
        .maxByOrNull { it.value.fatigueDeclines }
        ?.let { (phase, _) ->
            sentences += "Отказы по причине усталости чаще всего приходятся на фазу «${phase.label}» -- " +
                "возможно, стоит планировать больше отдыха в этот период."
        }

    return CorrelationInsights(sentences.distinct())
}

/** A single, non-alarming nudge shown on the calendar -- never more than one at a time. */
data class ProactiveSuggestion(val title: String, val message: String)

/** No intimacy/masturbation entry for this long reads as "a while", worth a gentle nudge. */
private const val LONG_ABSENCE_DAYS_THRESHOLD = 14L

/** Only recent declines count toward the "frequent fatigue" pattern -- old ones shouldn't nag forever. */
private const val FATIGUE_LOOKBACK_DAYS = 90L

private const val FATIGUE_DECLINE_THRESHOLD = 2

/**
 * Suggests a low-key, no-pressure idea (a massage, not sex) when the data shows either a long gap
 * since the last logged intimacy, or a recent cluster of "tired" declines -- exactly the two
 * triggers the feature was asked for. Purely rule-based on already-logged fields, no assumptions
 * beyond simple keyword/date matching.
 */
fun computeProactiveSuggestion(
    sexEntries: List<SexEntry>,
    masturbationEntries: List<MasturbationEntry>,
    proposalEntries: List<ProposalEntry>,
    today: LocalDate = LocalDate.now()
): ProactiveSuggestion? {
    val lastIntimacyDate = (sexEntries.map { it.date } + masturbationEntries.map { it.date })
        .mapNotNull { it.toLocalDateOrNull() }
        .maxOrNull()
    val daysSinceLast = lastIntimacyDate?.let { ChronoUnit.DAYS.between(it, today) }
    val longAbsence = daysSinceLast == null || daysSinceLast >= LONG_ABSENCE_DAYS_THRESHOLD

    val recentFatigueDeclines = proposalEntries.count { entry ->
        !entry.accepted &&
            entry.declineReason.contains("устал", ignoreCase = true) &&
            entry.date.toLocalDateOrNull()?.let { ChronoUnit.DAYS.between(it, today) <= FATIGUE_LOOKBACK_DAYS } == true
    }
    val frequentFatigue = recentFatigueDeclines >= FATIGUE_DECLINE_THRESHOLD

    if (!longAbsence && !frequentFatigue) return null

    val likedMassageBefore = sexEntries.any { it.notes.contains("массаж", ignoreCase = true) } ||
        masturbationEntries.any { it.notes.contains("массаж", ignoreCase = true) }
    val idea = if (likedMassageBefore) {
        "например, массаж без продолжения -- раньше вам обоим это нравилось"
    } else {
        "например, просто массаж без продолжения, без ожиданий"
    }

    val message = when {
        longAbsence && frequentFatigue ->
            "Давно не было близости, а среди недавних отказов часто звучит усталость. " +
                "Может, устроить спокойный вечер вдвоём -- $idea?"
        longAbsence ->
            "Давно не было записей о близости. Может, устроить расслабляющий вечер -- $idea?"
        else ->
            "В недавних отказах часто звучит усталость. Возможно, стоит запланировать больше отдыха -- $idea."
    }

    return ProactiveSuggestion(title = "Идея для вечера", message = message)
}
