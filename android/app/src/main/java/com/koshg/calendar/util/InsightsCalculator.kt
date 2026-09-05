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

/**
 * How the app counts proposal outcomes, which is not simply "what the proposal rows say".
 *
 * A logged sex entry is itself an acceptance: something was proposed, in whatever form, and it
 * went ahead. So a day with sex counts as an accepted proposal even when no proposal was ever
 * written down -- most encounters never get one, and without this the acceptance rate only ever
 * measures the occasions someone bothered to log a proposal, which skews it toward the refusals
 * (those are the ones people remember to record).
 *
 * Two rules keep that from double-counting or overriding the user:
 *  - A proposal and a sex entry on the same day count once between them, not twice.
 *  - An explicit "отклонено" stays declined. The outcome the user typed outranks one inferred
 *    from another entry, even a contradictory one. Only a proposal still awaiting an answer is
 *    resolved by same-day sex.
 */
data class ProposalOutcomes(
    val accepted: Int,
    val declined: Int,
    val pending: Int,
    /** Of [accepted], how many came from a sex entry rather than a proposal the user answered --
     *  lets the UI explain a number that would otherwise look wrong against the logged rows. */
    val fromSex: Int
) {
    val answered get() = accepted + declined
}

fun computeProposalOutcomes(
    proposalEntries: List<ProposalEntry>,
    sexEntries: List<SexEntry>
): ProposalOutcomes {
    val sexDates = sexEntries.mapTo(HashSet()) { it.date }
    val proposalDates = proposalEntries.mapTo(HashSet()) { it.date }

    var accepted = 0
    var declined = 0
    var pending = 0
    var fromSex = 0
    proposalEntries.forEach { entry ->
        when {
            entry.answered && entry.accepted -> accepted++
            entry.answered -> declined++
            entry.date in sexDates -> {
                accepted++
                fromSex++
            }
            else -> pending++
        }
    }
    // Days where it went ahead with no proposal row at all. Counted by date, so two entries on
    // one day are still one outcome.
    val undocumented = (sexDates - proposalDates).size
    return ProposalOutcomes(
        accepted = accepted + undocumented,
        declined = declined,
        pending = pending,
        fromSex = fromSex + undocumented
    )
}

/** One correlation finding, tagged with the phase it's about so the UI can prefix it with that
 *  phase's legend color for a quick visual anchor. */
data class CorrelationInsight(val phase: CyclePhase, val sentence: String)

data class CorrelationInsights(val insights: List<CorrelationInsight>)

/**
 * Looks for simple, sample-size-gated patterns between cycle phase and intimacy initiative/
 * acceptance -- everything here is plain aggregation over already-logged entries, no inference
 * beyond counting, so results stay predictable and explainable.
 */
fun computeCorrelationInsights(
    periods: List<PeriodEntry>,
    sexEntries: List<SexEntry>,
    proposalEntries: List<ProposalEntry>,
    marginDays: Int = 0,
    lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
): CorrelationInsights {
    if (periods.size < 2) return CorrelationInsights(emptyList())

    // Both cycle-model parameters have to be the ones the calendar itself is drawn with, or an
    // entry gets bucketed into a different phase here than the day it sits on is painted with --
    // the app would then say two different things about the same date.
    fun phaseOf(dateStr: String): CyclePhase? =
        dateStr.toLocalDateOrNull()?.let { cyclePhaseFor(it, periods, marginDays, lutealPhaseDays) }

    val tallies = CyclePhase.entries.associateWith { PhaseTally() }
    val sexDates = sexEntries.mapTo(HashSet()) { it.date }
    val proposalDates = proposalEntries.mapTo(HashSet()) { it.date }

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
        // An unanswered proposal still counts toward who initiates. It has no accepted/declined
        // outcome of its own yet -- unless sex was logged that day, which is the answer.
        if (!entry.answered) {
            if (entry.date in sexDates) tally.accepted++
            return@forEach
        }
        if (entry.accepted) {
            tally.accepted++
        } else {
            tally.declined++
            if (entry.declineReason.contains("устал", ignoreCase = true)) tally.fatigueDeclines++
        }
    }

    // Sex on a day with no proposal row is an accepted proposal here too, or this screen's
    // per-phase rates would contradict the Proposals card -- see [computeProposalOutcomes].
    (sexDates - proposalDates).forEach { date ->
        val tally = phaseOf(date)?.let { tallies[it] } ?: return@forEach
        tally.accepted++
    }

    val insights = mutableListOf<CorrelationInsight>()

    val overallPartnerShare = tallies.values.sumOf { it.partnerInitiated }
        .toFloat()
        .let { partnerTotal -> partnerTotal / tallies.values.sumOf { it.totalInitiations }.coerceAtLeast(1) }

    tallies.entries
        .filter { it.value.totalInitiations >= MIN_PHASE_SAMPLES }
        .maxByOrNull { it.value.partnerInitiated.toFloat() / it.value.totalInitiations }
        ?.let { (phase, tally) ->
            val share = tally.partnerInitiated.toFloat() / tally.totalInitiations
            if (share - overallPartnerShare >= INITIATOR_SKEW_THRESHOLD) {
                insights += CorrelationInsight(
                    phase,
                    "В фазу «${phase.label}» инициатива чаще исходит от партнёра (${(share * 100).roundToInt()}% случаев)."
                )
            }
        }

    val withProposals = tallies.entries.filter { it.value.totalProposals >= MIN_PHASE_SAMPLES }
    withProposals.maxByOrNull { it.value.accepted.toFloat() / it.value.totalProposals }?.let { (phase, tally) ->
        val rate = (tally.accepted.toFloat() / tally.totalProposals * 100).roundToInt()
        insights += CorrelationInsight(phase, "В фазу «${phase.label}» процент принятых предложений максимален ($rate%).")
    }
    withProposals.minByOrNull { it.value.accepted.toFloat() / it.value.totalProposals }?.let { (phase, tally) ->
        val declineRate = ((1f - tally.accepted.toFloat() / tally.totalProposals) * 100).roundToInt()
        if (declineRate > 0) {
            insights += CorrelationInsight(phase, "В фазу «${phase.label}» отказов больше всего ($declineRate%).")
        }
    }

    tallies.entries
        .filter { it.value.fatigueDeclines >= FATIGUE_DECLINE_MIN_IN_PHASE }
        .maxByOrNull { it.value.fatigueDeclines }
        ?.let { (phase, _) ->
            insights += CorrelationInsight(
                phase,
                "Отказы по причине усталости чаще всего приходятся на фазу «${phase.label}» -- " +
                    "возможно, стоит планировать больше отдыха в этот период."
            )
        }

    return CorrelationInsights(insights.distinct())
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
        entry.answered && !entry.accepted &&
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
