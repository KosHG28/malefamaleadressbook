package com.koshg.interlude.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.koshg.interlude.R
import com.koshg.interlude.data.MasturbationEntry
import com.koshg.interlude.data.PeriodEntry
import com.koshg.interlude.data.ProposalEntry
import com.koshg.interlude.data.SexEntry
import com.koshg.interlude.ui.theme.appColors
import com.koshg.interlude.ui.theme.phaseColor
import com.koshg.interlude.util.CorrelationInsight
import com.koshg.interlude.util.computeCorrelationInsights
import com.koshg.interlude.util.computeProposalOutcomes
import com.koshg.interlude.util.monthShortLabel
import com.koshg.interlude.util.shortDateLabel
import com.koshg.interlude.util.toLocalDateOrNull
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    periods: List<PeriodEntry>,
    sexEntries: List<SexEntry>,
    proposalEntries: List<ProposalEntry>,
    masturbationEntries: List<MasturbationEntry>,
    isIrregular: Boolean,
    marginDays: Int,
    lutealPhaseDays: Int,
    onClose: () -> Unit,
    onOpenYearOverview: () -> Unit
) {
    val appColors = appColors()
    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

    // This screen is shown as a plain in-place overlay (a boolean flag in CalendarScreen), not
    // through a Dialog/Popup window -- those intercept the system back gesture on their own, but
    // this doesn't, so without this handler swiping back here would fall through to the Activity
    // and close the whole app instead of just this screen.
    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close), tint = appColors.textPrimary)
                }
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                IconButton(onClick = onOpenYearOverview) {
                    Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.year_overview_title), tint = appColors.textPrimary)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { CycleLengthSection(periods, isIrregular) }
                    item { FrequencySection(stringResource(R.string.agenda_intimacy), sexEntries.map { it.date }, appColors.intimacy) }
                    item { ProposalStatsSection(proposalEntries, sexEntries) }
                    item { FrequencySection(stringResource(R.string.agenda_masturbation), masturbationEntries.map { it.date }, appColors.solo) }
                    item {
                        CorrelationInsightsSection(
                            periods,
                            sexEntries,
                            proposalEntries,
                            marginDays,
                            lutealPhaseDays
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val appColors = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = appColors.warmSurface.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    val appColors = appColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = appColors.textSecondary)
    }
}

@Composable
private fun CycleLengthSection(periods: List<PeriodEntry>, isIrregular: Boolean) {
    val appColors = appColors()
    val sortedStarts = remember(periods) {
        periods.mapNotNull { it.startDate.toLocalDateOrNull() }.sorted()
    }
    val lengths = remember(sortedStarts) {
        sortedStarts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
    }
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.history_section_cycle_length)) {
        if (lengths.isEmpty()) {
            Text(
                stringResource(R.string.history_need_two_cycles),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
        } else {
            if (isIrregular) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.warning.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = appColors.warning, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.history_irregular),
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors.warning
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            val average = lengths.average().roundToInt()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock(stringResource(R.string.history_average), daysLabel(average))
                StatBlock(stringResource(R.string.history_minimum), daysLabel(lengths.min()))
                StatBlock(stringResource(R.string.history_maximum), daysLabel(lengths.max()))
            }
            Spacer(Modifier.height(10.dp))
            sortedStarts.zipWithNext().forEachIndexed { index, (a, b) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(
                            R.string.history_date_range,
                            context.shortDateLabel(a),
                            context.shortDateLabel(b)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.textSecondary
                    )
                    Text(daysLabel(lengths[index]), style = MaterialTheme.typography.bodySmall, color = appColors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun FrequencySection(title: String, dates: List<String>, color: Color) {
    val appColors = appColors()
    val context = LocalContext.current
    val today = LocalDate.now()
    val months = remember { (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) } }
    val counts = remember(dates) {
        val byMonth = dates.mapNotNull { it.toLocalDateOrNull() }
            .groupingBy { YearMonth.from(it) }
            .eachCount()
        months.map { byMonth[it] ?: 0 }
    }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    SectionCard(title = title) {
        if (counts.all { it == 0 }) {
            Text(
                stringResource(R.string.history_no_entries),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEachIndexed { index, month ->
                    val count = counts[index]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            context.monthShortLabel(month),
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors.textSecondary,
                            modifier = Modifier.width(56.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(50))
                                .background(appColors.warmSurface.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = (count.toFloat() / maxCount).coerceIn(0.05f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(color)
                            )
                        }
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors.textPrimary,
                            modifier = Modifier.width(18.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalStatsSection(proposals: List<ProposalEntry>, sexEntries: List<SexEntry>) {
    val appColors = appColors()
    SectionCard(title = stringResource(R.string.history_section_proposals)) {
        if (proposals.isEmpty() && sexEntries.isEmpty()) {
            Text(
                stringResource(R.string.history_no_entries),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
        } else {
            // A logged encounter counts as an accepted proposal even without a proposal row, and
            // a still-pending proposal has no outcome to count at all -- see the rules on
            // [computeProposalOutcomes].
            val outcomes = remember(proposals, sexEntries) {
                computeProposalOutcomes(proposals, sexEntries)
            }
            val pendingCount = outcomes.pending
            val accepted = outcomes.accepted
            val total = outcomes.answered
            if (total > 0) {
                val percent = accepted * 100 / total
                Text(
                    stringResource(R.string.history_accepted_of, accepted, total, percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textPrimary
                )
            }
            if (pendingCount > 0) {
                Text(
                    stringResource(R.string.history_pending, pendingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.warning
                )
            }
            if (outcomes.fromSex > 0) {
                Text(
                    stringResource(R.string.history_from_intimacy, outcomes.fromSex),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(appColors.warmSurface.copy(alpha = 0.5f))
            ) {
                if (accepted > 0) {
                    Box(
                        modifier = Modifier
                            .weight(accepted.toFloat())
                            .fillMaxHeight()
                            .background(appColors.proposalAccepted)
                    )
                }
                if (total - accepted > 0) {
                    Box(
                        modifier = Modifier
                            .weight((total - accepted).toFloat())
                            .fillMaxHeight()
                            .background(appColors.proposalDeclined)
                    )
                }
            }
        }
    }
}

/** Simple, sample-size-gated phase-vs-initiative/acceptance patterns -- see [computeCorrelationInsights]. */
@Composable
private fun CorrelationInsightsSection(
    periods: List<PeriodEntry>,
    sexEntries: List<SexEntry>,
    proposalEntries: List<ProposalEntry>,
    marginDays: Int,
    lutealPhaseDays: Int
) {
    val appColors = appColors()
    val insights = remember(periods, sexEntries, proposalEntries, marginDays, lutealPhaseDays) {
        computeCorrelationInsights(periods, sexEntries, proposalEntries, marginDays, lutealPhaseDays)
    }
    SectionCard(title = stringResource(R.string.history_section_correlations)) {
        if (insights.insights.isEmpty()) {
            Text(
                stringResource(R.string.history_not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insights.insights.forEach { insight ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(appColors.phaseColor(insight.phase))
                        )
                        Text(
                            insightSentence(insight),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

/** "5 days" / "5 дн." — a plural, since Russian inflects the word by count and picking the form
 *  in code would bake one language's rules into the layout. */
@Composable
private fun daysLabel(days: Int): String =
    pluralStringResource(R.plurals.days_short, days, days)

/** Turns a [CorrelationInsight] into the sentence for it. The engine returns what it found, not
 *  the words -- see [CorrelationInsight] -- and FATIGUE_CLUSTER is the one kind whose wording
 *  takes only the phase, with no percentage to place. */
@Composable
private fun insightSentence(insight: CorrelationInsight): String {
    val phase = stringResource(insight.phase.labelRes)
    return if (insight.kind == CorrelationInsight.Kind.FATIGUE_CLUSTER) {
        stringResource(insight.kind.textRes, phase)
    } else {
        stringResource(insight.kind.textRes, phase, insight.percent)
    }
}
