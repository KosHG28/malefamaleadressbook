package com.koshg.interlude.util

import androidx.annotation.StringRes
import com.koshg.interlude.R
import com.koshg.interlude.data.DeclineReason
import com.koshg.interlude.data.Initiator
import com.koshg.interlude.data.MasturbationEntry
import com.koshg.interlude.data.PeriodEntry
import com.koshg.interlude.data.ProposalEntry
import com.koshg.interlude.data.SexEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Below this many samples in a phase bucket, a rate/skew is too noisy to state as a pattern. */
private const val MIN_PHASE_SAMPLES = 4

/** How much a phase's partner-initiated share must beat the overall average to be worth calling out. */
private const val INITIATOR_SKEW_THRESHOLD = 0.2f

/** How many tiredness declines in one phase before it reads as a pattern, not noise. */
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
 *  - An explicit decline stays declined. The outcome the user typed outranks one inferred
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

/**
 * One correlation finding: which phase it is about (so the UI can prefix it with that phase's
 * legend color for a quick visual anchor), what kind of finding it is, and the percentage it
 * turns on.
 *
 * The finding is data, not a sentence. Building the sentence here would hardcode one language
 * into the statistics engine, and the wording has to come from the resources that follow the
 * device's locale like everything else on screen.
 */
data class CorrelationInsight(val phase: CyclePhase, val kind: Kind, val percent: Int) {
    enum class Kind(@StringRes val textRes: Int) {
        PARTNER_INITIATES(R.string.insight_partner_initiates),
        HIGHEST_ACCEPTANCE(R.string.insight_highest_acceptance),
        MOST_DECLINES(R.string.insight_most_declines),

        /** Carries no percentage -- its wording takes only the phase name. */
        FATIGUE_CLUSTER(R.string.insight_fatigue_cluster)
    }
}

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
            if (DeclineReason.fromStorage(entry.declineReason) == DeclineReason.FATIGUE) {
                tally.fatigueDeclines++
            }
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
                    CorrelationInsight.Kind.PARTNER_INITIATES,
                    (share * 100).roundToInt()
                )
            }
        }

    val withProposals = tallies.entries.filter { it.value.totalProposals >= MIN_PHASE_SAMPLES }
    withProposals.maxByOrNull { it.value.accepted.toFloat() / it.value.totalProposals }?.let { (phase, tally) ->
        val rate = (tally.accepted.toFloat() / tally.totalProposals * 100).roundToInt()
        insights += CorrelationInsight(phase, CorrelationInsight.Kind.HIGHEST_ACCEPTANCE, rate)
    }
    withProposals.minByOrNull { it.value.accepted.toFloat() / it.value.totalProposals }?.let { (phase, tally) ->
        val declineRate = ((1f - tally.accepted.toFloat() / tally.totalProposals) * 100).roundToInt()
        if (declineRate > 0) {
            insights += CorrelationInsight(phase, CorrelationInsight.Kind.MOST_DECLINES, declineRate)
        }
    }

    tallies.entries
        .filter { it.value.fatigueDeclines >= FATIGUE_DECLINE_MIN_IN_PHASE }
        .maxByOrNull { it.value.fatigueDeclines }
        ?.let { (phase, _) ->
            insights += CorrelationInsight(phase, CorrelationInsight.Kind.FATIGUE_CLUSTER, percent = 0)
        }

    return CorrelationInsights(insights.distinct())
}

/**
 * A single, non-alarming nudge shown on the calendar -- never more than one at a time.
 *
 * Like [CorrelationInsight], this carries what was noticed rather than the words for it: [reason]
 * is why the nudge fired and [idea] is which suggestion it settled on, and the UI turns the pair
 * into a sentence in the reader's own language.
 */
data class ProactiveSuggestion(val reason: Reason, val idea: Idea) {
    enum class Reason(@StringRes val textRes: Int) {
        LONG_ABSENCE(R.string.suggestion_long_absence),
        FATIGUE(R.string.suggestion_fatigue),
        BOTH(R.string.suggestion_both)
    }

    enum class Idea(@StringRes val textRes: Int) {
        /** The couple's own notes mention a massage they liked. */
        MASSAGE_LIKED(R.string.suggestion_idea_massage_liked),
        MASSAGE_GENERIC(R.string.suggestion_idea_massage_generic)
    }
}

/** Words looked for in free-text notes to tell whether a massage went down well before.
 *
 *  Notes are written in whatever language the user pleases, and unlike a decline reason there is
 *  no code to store for them -- so this is a best-effort keyword list per supported language, not
 *  a reliable signal. Getting it wrong only picks the more generic of two friendly phrasings. */
private val MASSAGE_KEYWORDS = listOf("массаж", "massage")

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
            DeclineReason.fromStorage(entry.declineReason) == DeclineReason.FATIGUE &&
            entry.date.toLocalDateOrNull()?.let { ChronoUnit.DAYS.between(it, today) <= FATIGUE_LOOKBACK_DAYS } == true
    }
    val frequentFatigue = recentFatigueDeclines >= FATIGUE_DECLINE_THRESHOLD

    if (!longAbsence && !frequentFatigue) return null

    fun String.mentionsMassage() = MASSAGE_KEYWORDS.any { contains(it, ignoreCase = true) }
    val likedMassageBefore = sexEntries.any { it.notes.mentionsMassage() } ||
        masturbationEntries.any { it.notes.mentionsMassage() }

    val reason = when {
        longAbsence && frequentFatigue -> ProactiveSuggestion.Reason.BOTH
        longAbsence -> ProactiveSuggestion.Reason.LONG_ABSENCE
        else -> ProactiveSuggestion.Reason.FATIGUE
    }
    val idea = if (likedMassageBefore) {
        ProactiveSuggestion.Idea.MASSAGE_LIKED
    } else {
        ProactiveSuggestion.Idea.MASSAGE_GENERIC
    }
    return ProactiveSuggestion(reason, idea)
}
