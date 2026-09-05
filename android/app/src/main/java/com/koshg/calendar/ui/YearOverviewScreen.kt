package com.koshg.calendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.LocalMarkerColors
import com.koshg.calendar.ui.theme.MarkerKind
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.ui.theme.colorFor
import com.koshg.calendar.ui.theme.phaseColor
import com.koshg.calendar.util.CyclePhase
import com.koshg.calendar.util.cyclePhaseFor
import com.koshg.calendar.util.monthYearLabel
import java.time.YearMonth

/**
 * A 12-month-at-a-glance overview -- each month renders as a tiny mosaic of phase colors (no day
 * numbers, no weekday alignment, just a quick heat-map read of the year) so jumping to a distant
 * month doesn't mean paging through the main calendar one month at a time.
 *
 * Days carry the same marker rings the month grid draws, in the same colors and by the same
 * priority ([markerKindFor]), so a year of intimacy reads at a glance too -- that is most of what
 * makes a year view worth opening, and without it a whole year of entries was invisible here.
 */
@Composable
fun YearOverviewScreen(
    initialYear: Int,
    periods: List<PeriodEntry>,
    marginDays: Int,
    lutealPhaseDays: Int,
    sexDates: Set<String>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>,
    onClose: () -> Unit,
    onMonthClick: (YearMonth) -> Unit
) {
    val appColors = appColors()
    val haptics = LocalHaptics.current
    var year by remember { mutableStateOf(initialYear) }
    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

    // Shown as a plain in-place overlay, not a Dialog/Popup window, so it needs its own back
    // interception -- see HistoryScreen's BackHandler for why.
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

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().adaptiveContentWidth(),
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
                        sexDates = sexDates,
                        proposalByDate = proposalByDate,
                        masturbationDates = masturbationDates,
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
}

/** The marker ring inside a mosaic day, as a fraction of the day's own circle, plus the halo just
 *  outside it. Fractions rather than dp because the mosaic sizes its days by dividing the card
 *  width seven ways, so their diameter follows the screen. */
private const val MOSAIC_RING_FRACTION = 0.56f
private const val MOSAIC_RING_HALO_FRACTION = 0.64f
private val MOSAIC_RING_WIDTH = 2.dp

/** One day of the mosaic: what phase it fell in, and which marker ring (if any) it earned. */
private data class MosaicDay(val phase: CyclePhase?, val marker: MarkerKind?)

@Composable
private fun MonthMosaic(
    month: YearMonth,
    periods: List<PeriodEntry>,
    marginDays: Int,
    lutealPhaseDays: Int,
    sexDates: Set<String>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>,
    onClick: () -> Unit
) {
    val appColors = appColors()
    val markerColors = LocalMarkerColors.current
    val days = remember(
        month, periods, marginDays, lutealPhaseDays, sexDates, proposalByDate, masturbationDates
    ) {
        (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            MosaicDay(
                phase = cyclePhaseFor(date, periods, marginDays, lutealPhaseDays),
                marker = markerKindFor(date.toString(), sexDates, proposalByDate, masturbationDates)
            )
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
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Percent-based, like every other round element in the app (legend
                                // dots, day-cell pills) -- a plain square read as out of place here.
                                .clip(RoundedCornerShape(50))
                                .background(
                                    day.phase?.let { appColors.phaseColor(it) } ?: appColors.warmBackground
                                )
                        )
                        // Inset rather than drawn on the day's own edge: with only 2dp between
                        // neighbours, edge rings on two marked days in a row would touch and read
                        // as one shape. The white halo does the same job as the month grid's --
                        // keeps the ring visible when its color sits close to the fill beneath it.
                        day.marker?.let { marker ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(MOSAIC_RING_HALO_FRACTION)
                                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(MOSAIC_RING_FRACTION)
                                    .border(MOSAIC_RING_WIDTH, markerColors.colorFor(marker), CircleShape)
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
