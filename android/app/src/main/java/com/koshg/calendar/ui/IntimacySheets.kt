package com.koshg.calendar.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.Initiator
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.util.fullDateLabel
import com.koshg.calendar.util.toLocalDateOrNull
import java.time.LocalDate

enum class AddType(val label: String) {
    Period("Месячные"),
    Sex("Секс"),
    Proposal("Предложение"),
    Masturbation("Мастурбация")
}

private fun AddType.icon(): ImageVector = when (this) {
    AddType.Period -> Icons.Default.WaterDrop
    AddType.Sex -> Icons.Default.Favorite
    AddType.Proposal -> Icons.Default.FavoriteBorder
    AddType.Masturbation -> Icons.Default.SelfImprovement
}

@Composable
private fun AddType.chipColor(): Color {
    val colors = appColors()
    return when (this) {
        AddType.Period -> colors.menstrual
        AddType.Sex -> colors.intimacy
        AddType.Proposal -> colors.proposalAccepted
        AddType.Masturbation -> colors.solo
    }
}

@Composable
private fun TypeChipRow(selected: AddType, onSelect: (AddType) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AddType.entries.forEach { type ->
            val color = type.chipColor()
            val isSelected = type == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) color else color.copy(alpha = 0.15f))
                    .clickable {
                        haptics.perform(HapticEvent.Tap)
                        onSelect(type)
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    type.icon(),
                    contentDescription = null,
                    tint = if (isSelected) Color.White else color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else color
                )
            }
        }
    }
}

/** Rounded, accent-tinted text field colors shared by every sheet, in place of stock Material outlines. */
@Composable
internal fun sheetFieldColors(): TextFieldColors {
    val appColors = appColors()
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = appColors.accent,
        focusedLabelColor = appColors.accent,
        cursorColor = appColors.accent,
        unfocusedBorderColor = appColors.textSecondary.copy(alpha = 0.35f),
        unfocusedLabelColor = appColors.textSecondary,
        focusedTextColor = appColors.textPrimary,
        unfocusedTextColor = appColors.textPrimary
    )
}

internal val sheetFieldShape = RoundedCornerShape(14.dp)

/**
 * Single sheet for creating a new entry of any kind — a row of colored type chips up top swaps
 * the fields below, so picking what to log and filling it in happens in one open/close instead
 * of a chooser sheet followed by a second, type-specific one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedAddSheet(
    initialType: AddType,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSavePeriod: (PeriodEntry) -> Unit,
    onSaveSex: (SexEntry) -> Unit,
    onSaveProposal: (ProposalEntry) -> Unit,
    onSaveMasturbation: (MasturbationEntry) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHaptics.current
    val appColors = appColors()

    var type by remember { mutableStateOf(initialType) }
    var date by remember { mutableStateOf(initialDate) }
    var notes by remember { mutableStateOf("") }

    var initiator by remember { mutableStateOf(Initiator.ME) }
    var orgasmCount by remember { mutableStateOf(0) }
    var accepted by remember { mutableStateOf(true) }
    var declineReason by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.warmSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Новая запись", style = MaterialTheme.typography.titleLarge, color = appColors.textPrimary)

            TypeChipRow(selected = type) { type = it }

            DateField(date) { date = it }

            when (type) {
                AddType.Period -> Unit

                AddType.Sex -> {
                    InitiatorSelector(initiator) { initiator = it }
                    CountStepper("Количество оргазмов", orgasmCount) { orgasmCount = it }
                }

                AddType.Proposal -> {
                    InitiatorSelector(initiator) { initiator = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = accepted, onCheckedChange = {
                            haptics.perform(HapticEvent.Toggle)
                            accepted = it
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(if (accepted) "Принято" else "Отклонено", color = appColors.textPrimary)
                    }
                    if (!accepted) {
                        OutlinedTextField(
                            value = declineReason,
                            onValueChange = { declineReason = it },
                            label = { Text("Причина отказа") },
                            shape = sheetFieldShape,
                            colors = sheetFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                AddType.Masturbation -> {
                    InitiatorSelector(initiator) { initiator = it }
                    CountStepper("Количество оргазмов", orgasmCount) { orgasmCount = it }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            SheetActions(
                onDismiss = onDismiss,
                onDelete = null,
                onSave = {
                    when (type) {
                        AddType.Period -> onSavePeriod(
                            PeriodEntry(id = newEventId(), startDate = date.toString(), notes = notes.trim())
                        )

                        AddType.Sex -> onSaveSex(
                            SexEntry(
                                id = newEventId(),
                                date = date.toString(),
                                initiator = initiator.storageValue,
                                orgasmCount = orgasmCount,
                                notes = notes.trim()
                            )
                        )

                        AddType.Proposal -> onSaveProposal(
                            ProposalEntry(
                                id = newEventId(),
                                date = date.toString(),
                                initiator = initiator.storageValue,
                                accepted = accepted,
                                declineReason = if (accepted) "" else declineReason.trim(),
                                notes = notes.trim()
                            )
                        )

                        AddType.Masturbation -> onSaveMasturbation(
                            MasturbationEntry(
                                id = newEventId(),
                                date = date.toString(),
                                person = initiator.storageValue,
                                orgasmCount = orgasmCount,
                                notes = notes.trim()
                            )
                        )
                    }
                }
            )
        }
    }
}

/** A rounded, tappable pill showing the picked date — replaces a stock read-only text field. */
@Composable
private fun DateField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val appColors = appColors()
    val haptics = LocalHaptics.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(sheetFieldShape)
            .background(appColors.warmBackground)
            .border(1.dp, appColors.accent.copy(alpha = 0.3f), sheetFieldShape)
            .clickable {
                haptics.perform(HapticEvent.Tap)
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onDateChange(LocalDate.of(y, m + 1, d)) },
                    date.year, date.monthValue - 1, date.dayOfMonth
                ).show()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(20.dp))
        Text(
            fullDateLabel(date),
            style = MaterialTheme.typography.bodyLarge,
            color = appColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = appColors.textSecondary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitiatorSelector(selected: Initiator, onSelect: (Initiator) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    Column {
        Text("Инициатор", style = MaterialTheme.typography.labelLarge, color = appColors.textSecondary)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Initiator.entries.forEachIndexed { index, entry ->
                val isSelected = selected == entry
                SegmentedButton(
                    selected = isSelected,
                    onClick = {
                        haptics.perform(HapticEvent.Tap)
                        onSelect(entry)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Initiator.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = appColors.accent,
                        activeContentColor = Color.White,
                        activeBorderColor = appColors.accent,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = appColors.textPrimary,
                        inactiveBorderColor = appColors.textSecondary.copy(alpha = 0.35f)
                    )
                ) { Text(entry.label, color = if (isSelected) Color.White else appColors.textPrimary) }
            }
        }
    }
}

@Composable
private fun CountStepper(label: String, count: Int, onCountChange: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    val appColors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = appColors.textSecondary, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            if (count > 0) {
                haptics.perform(HapticEvent.Tap)
                onCountChange(count - 1)
            }
        }) { Icon(Icons.Default.Remove, contentDescription = "Меньше", tint = appColors.accent) }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = appColors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = {
            haptics.perform(HapticEvent.Tap)
            onCountChange(count + 1)
        }) { Icon(Icons.Default.Add, contentDescription = "Больше", tint = appColors.accent) }
    }
}

@Composable
private fun SheetActions(onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: () -> Unit) {
    val appColors = appColors()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss) { Text("Отмена", color = appColors.textSecondary) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = Color.White)
        ) { Text("Сохранить") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSheet(
    initialDate: LocalDate,
    entry: PeriodEntry?,
    onDismiss: () -> Unit,
    onSave: (PeriodEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.startDate?.toLocalDateOrNull() ?: initialDate) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) "Начало месячных" else "Редактировать запись",
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        PeriodEntry(
                            id = entry?.id ?: newEventId(),
                            startDate = date.toString(),
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SexSheet(
    initialDate: LocalDate,
    entry: SexEntry?,
    onDismiss: () -> Unit,
    onSave: (SexEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var initiator by remember { mutableStateOf(entry?.initiator?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var orgasmCount by remember { mutableStateOf(entry?.orgasmCount ?: 0) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) "Близость" else "Редактировать запись",
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            InitiatorSelector(initiator) { initiator = it }
            CountStepper("Количество оргазмов", orgasmCount) { orgasmCount = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        SexEntry(
                            id = entry?.id ?: newEventId(),
                            date = date.toString(),
                            initiator = initiator.storageValue,
                            orgasmCount = orgasmCount,
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalSheet(
    initialDate: LocalDate,
    entry: ProposalEntry?,
    onDismiss: () -> Unit,
    onSave: (ProposalEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHaptics.current
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var initiator by remember { mutableStateOf(entry?.initiator?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var accepted by remember { mutableStateOf(entry?.accepted ?: true) }
    var declineReason by remember { mutableStateOf(entry?.declineReason ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) "Предложение близости" else "Редактировать запись",
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            InitiatorSelector(initiator) { initiator = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = accepted, onCheckedChange = {
                    haptics.perform(HapticEvent.Toggle)
                    accepted = it
                })
                Spacer(Modifier.width(8.dp))
                Text(if (accepted) "Принято" else "Отклонено", color = appColors.textPrimary)
            }
            if (!accepted) {
                OutlinedTextField(
                    value = declineReason,
                    onValueChange = { declineReason = it },
                    label = { Text("Причина отказа") },
                    shape = sheetFieldShape,
                    colors = sheetFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        ProposalEntry(
                            id = entry?.id ?: newEventId(),
                            date = date.toString(),
                            initiator = initiator.storageValue,
                            accepted = accepted,
                            declineReason = if (accepted) "" else declineReason.trim(),
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasturbationSheet(
    initialDate: LocalDate,
    entry: MasturbationEntry?,
    onDismiss: () -> Unit,
    onSave: (MasturbationEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = appColors()
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var person by remember { mutableStateOf(entry?.person?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var orgasmCount by remember { mutableStateOf(entry?.orgasmCount ?: 0) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) "Мастурбация" else "Редактировать запись",
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )
            DateField(date) { date = it }
            InitiatorSelector(person) { person = it }
            CountStepper("Количество оргазмов", orgasmCount) { orgasmCount = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                onDismiss = onDismiss,
                onDelete = onDelete,
                onSave = {
                    onSave(
                        MasturbationEntry(
                            id = entry?.id ?: newEventId(),
                            date = date.toString(),
                            person = person.storageValue,
                            orgasmCount = orgasmCount,
                            notes = notes.trim()
                        )
                    )
                }
            )
        }
    }
}
