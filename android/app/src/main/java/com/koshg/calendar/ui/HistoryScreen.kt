package com.koshg.calendar.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.ui.theme.phaseColor
import com.koshg.calendar.util.computeCorrelationInsights
import com.koshg.calendar.util.monthShortLabel
import com.koshg.calendar.util.shortDateLabel
import com.koshg.calendar.util.toLocalDateOrNull
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
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = appColors.textPrimary)
                }
                Text(
                    text = "История и тренды",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                IconButton(onClick = onOpenYearOverview) {
                    Icon(Icons.Default.GridView, contentDescription = "Год целиком", tint = appColors.textPrimary)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { CycleLengthSection(periods, isIrregular) }
                    item { FrequencySection("Близость", sexEntries.map { it.date }, appColors.intimacy) }
                    item { ProposalStatsSection(proposalEntries) }
                    item { FrequencySection("Мастурбация", masturbationEntries.map { it.date }, appColors.solo) }
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

    SectionCard(title = "Длина цикла") {
        if (lengths.isEmpty()) {
            Text(
                "Добавьте хотя бы два цикла месячных, чтобы увидеть статистику.",
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
                        "Цикл нерегулярный -- длины сильно разбросаны, прогноз менее точен",
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors.warning
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            val average = lengths.average().roundToInt()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatBlock("Средняя", "$average дн.")
                StatBlock("Минимум", "${lengths.min()} дн.")
                StatBlock("Максимум", "${lengths.max()} дн.")
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
                        "${shortDateLabel(a)} → ${shortDateLabel(b)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.textSecondary
                    )
                    Text("${lengths[index]} дн.", style = MaterialTheme.typography.bodySmall, color = appColors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun FrequencySection(title: String, dates: List<String>, color: Color) {
    val appColors = appColors()
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
                "Записей пока нет.",
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                months.forEachIndexed { index, month ->
                    val count = counts[index]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            monthShortLabel(month),
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
private fun ProposalStatsSection(proposals: List<ProposalEntry>) {
    val appColors = appColors()
    SectionCard(title = "Предложения") {
        if (proposals.isEmpty()) {
            Text(
                "Записей пока нет.",
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
        } else {
            val accepted = proposals.count { it.accepted }
            val total = proposals.size
            val percent = accepted * 100 / total
            Text(
                "Принято $accepted из $total ($percent%)",
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textPrimary
            )
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
    SectionCard(title = "Корреляции") {
        if (insights.insights.isEmpty()) {
            Text(
                "Пока недостаточно записей, чтобы увидеть закономерности по фазам цикла.",
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
                            insight.sentence,
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
