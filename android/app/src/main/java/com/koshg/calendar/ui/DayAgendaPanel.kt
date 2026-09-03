package com.koshg.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.CalendarEvent
import com.koshg.calendar.data.Initiator
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.LocalMarkerColors
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.util.CyclePhase
import com.koshg.calendar.util.dayAgendaLabel
import com.koshg.calendar.util.phaseTipForMen
import java.time.LocalDate

@Composable
fun DayAgendaPanel(
    selectedDate: LocalDate,
    phase: CyclePhase?,
    events: List<CalendarEvent>,
    periodEntry: PeriodEntry?,
    sexEntry: SexEntry?,
    proposalEntry: ProposalEntry?,
    masturbationEntries: List<MasturbationEntry>,
    onEventClick: (CalendarEvent) -> Unit,
    onPeriodClick: (PeriodEntry) -> Unit,
    onSexClick: (SexEntry) -> Unit,
    onProposalClick: (ProposalEntry) -> Unit,
    onMasturbationClick: (MasturbationEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAnything = events.isNotEmpty() || periodEntry != null || sexEntry != null ||
        proposalEntry != null || masturbationEntries.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        val appColors = appColors()
        val haptics = LocalHaptics.current
        // Off by default -- a tap-to-reveal tip for what tends to suit this phase, rather than
        // a permanent banner that would repeat itself every time a day gets selected.
        var showPhaseTip by remember(selectedDate) { mutableStateOf(false) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayAgendaLabel(selectedDate).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                color = appColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (phase != null) {
                IconButton(
                    onClick = {
                        haptics.perform(HapticEvent.Tap)
                        showPhaseTip = !showPhaseTip
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Подсказка по фазе цикла",
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showPhaseTip && phase != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (phase != null) {
                Text(
                    text = phaseTipForMen(phase, selectedDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!hasAnything) {
            Text(
                text = "На этот день записей нет. Нажмите «+», чтобы добавить.",
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { "event-${it.id}" }) { event ->
                EventRow(event, onClick = { onEventClick(event) })
            }
            periodEntry?.let { entry ->
                item(key = "period-${entry.id}") {
                    PeriodRow(entry, onClick = { onPeriodClick(entry) })
                }
            }
            sexEntry?.let { entry ->
                item(key = "sex-${entry.id}") {
                    SexRow(entry, onClick = { onSexClick(entry) })
                }
            }
            proposalEntry?.let { entry ->
                item(key = "proposal-${entry.id}") {
                    ProposalRow(entry, onClick = { onProposalClick(entry) })
                }
            }
            items(masturbationEntries, key = { "masturbation-${it.id}" }) { entry ->
                MasturbationRow(entry, onClick = { onMasturbationClick(entry) })
            }
        }
    }
}

@Composable
private fun AgendaRow(
    leadingColor: Color,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    val appColors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.warmSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(leadingColor)
        )
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = appColors.textSecondary)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit) {
    val timeLabel = if (event.allDay) {
        "Весь день"
    } else {
        listOfNotNull(event.startTime, event.endTime).joinToString(" – ")
    }
    AgendaRow(
        leadingColor = Color(event.color),
        icon = {},
        title = event.title,
        subtitle = listOfNotNull(timeLabel.ifBlank { null }, event.notes.ifBlank { null }).joinToString(" · "),
        onClick = onClick
    )
}

@Composable
private fun PeriodRow(entry: PeriodEntry, onClick: () -> Unit) {
    val colors = appColors()
    AgendaRow(
        leadingColor = colors.period,
        icon = { Icon(Icons.Default.WaterDrop, contentDescription = null, tint = colors.period, modifier = Modifier.size(20.dp)) },
        title = "Начало месячных",
        subtitle = entry.notes.ifBlank { null },
        onClick = onClick
    )
}

@Composable
private fun SexRow(entry: SexEntry, onClick: () -> Unit) {
    // Same colors the calendar's own marker rings use, so a day's ring and the row it expands
    // into never disagree -- including after the user recolors a marker in Settings.
    val colors = LocalMarkerColors.current
    val parts = buildList {
        add("Инициатор: ${Initiator.fromStorage(entry.initiator).label}")
        if (entry.orgasmCount > 0) add("Оргазмов: ${entry.orgasmCount}")
        if (entry.notes.isNotBlank()) add(entry.notes)
    }
    AgendaRow(
        leadingColor = colors.sex,
        icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = colors.sex, modifier = Modifier.size(20.dp)) },
        title = "Близость",
        subtitle = parts.joinToString(" · "),
        onClick = onClick
    )
}

@Composable
private fun ProposalRow(entry: ProposalEntry, onClick: () -> Unit) {
    val colors = LocalMarkerColors.current
    val accentColor = when {
        !entry.answered -> colors.proposalPending
        entry.accepted -> colors.proposalAccepted
        else -> colors.proposalDeclined
    }
    val parts = buildList {
        add("От: ${Initiator.fromStorage(entry.initiator).label}")
        add(
            when {
                !entry.answered -> "ожидает ответа"
                entry.accepted -> "принято"
                else -> "отклонено"
            }
        )
        if (entry.answered && !entry.accepted && entry.declineReason.isNotBlank()) {
            add("причина: ${entry.declineReason}")
        }
        if (entry.notes.isNotBlank()) add(entry.notes)
    }
    AgendaRow(
        leadingColor = accentColor,
        icon = {
            Icon(
                when {
                    !entry.answered -> Icons.Default.QuestionMark
                    entry.accepted -> Icons.Default.Check
                    else -> Icons.Default.Close
                },
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        },
        title = "Предложение близости",
        subtitle = parts.joinToString(" · "),
        onClick = onClick
    )
}

@Composable
private fun MasturbationRow(entry: MasturbationEntry, onClick: () -> Unit) {
    val colors = LocalMarkerColors.current
    val parts = buildList {
        add(Initiator.fromStorage(entry.person).label)
        if (entry.orgasmCount > 0) add("Оргазмов: ${entry.orgasmCount}")
        if (entry.notes.isNotBlank()) add(entry.notes)
    }
    AgendaRow(
        leadingColor = colors.solo,
        icon = { Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = colors.solo, modifier = Modifier.size(20.dp)) },
        title = "Мастурбация",
        subtitle = parts.joinToString(" · "),
        onClick = onClick
    )
}
