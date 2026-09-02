package com.koshg.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.ui.theme.phaseColor
import com.koshg.calendar.util.cyclePhaseFor
import com.koshg.calendar.util.monthYearLabel
import java.time.YearMonth

/**
 * A 12-month-at-a-glance overview -- each month renders as a tiny mosaic of phase colors (no day
 * numbers, no weekday alignment, just a quick heat-map read of the year) so jumping to a distant
 * month doesn't mean paging through the main calendar one month at a time.
 */
@Composable
fun YearOverviewScreen(
    initialYear: Int,
    periods: List<PeriodEntry>,
    marginDays: Int,
    lutealPhaseDays: Int,
    onClose: () -> Unit,
    onMonthClick: (YearMonth) -> Unit
) {
    val appColors = appColors()
    val haptics = LocalHaptics.current
    var year by remember { mutableStateOf(initialYear) }
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
                    text = "Год целиком",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { haptics.perform(HapticEvent.Tap); year-- }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий год", tint = appColors.textPrimary)
                }
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary
                )
                IconButton(onClick = { haptics.perform(HapticEvent.Tap); year++ }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Следующий год", tint = appColors.textPrimary)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(12, key = { it }) { monthIndex ->
                    val month = YearMonth.of(year, monthIndex + 1)
                    MonthMosaic(
                        month = month,
                        periods = periods,
                        marginDays = marginDays,
                        lutealPhaseDays = lutealPhaseDays,
                        onClick = {
                            haptics.perform(HapticEvent.Select)
                            onMonthClick(month)
                        }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun MonthMosaic(
    month: YearMonth,
    periods: List<PeriodEntry>,
    marginDays: Int,
    lutealPhaseDays: Int,
    onClick: () -> Unit
) {
    val appColors = appColors()
    val dayPhases = remember(month, periods, marginDays, lutealPhaseDays) {
        (1..month.lengthOfMonth()).map { day ->
            cyclePhaseFor(month.atDay(day), periods, marginDays, lutealPhaseDays)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.warmSurface.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            monthYearLabel(month.atDay(1)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = appColors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        dayPhases.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                week.forEach { phase ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(phase?.let { appColors.phaseColor(it) } ?: appColors.warmBackground)
                    )
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
