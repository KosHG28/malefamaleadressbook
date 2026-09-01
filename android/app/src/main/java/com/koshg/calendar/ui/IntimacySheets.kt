package com.koshg.calendar.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.Initiator
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.util.fullDateLabel
import com.koshg.calendar.util.toLocalDateOrNull
import java.time.LocalDate

enum class AddType(val label: String) {
    Event("Событие"),
    Period("Месячные"),
    Sex("Секс"),
    Proposal("Предложение"),
    Masturbation("Мастурбация")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChooserSheet(onDismiss: () -> Unit, onPick: (AddType) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Что добавить?", style = MaterialTheme.typography.titleLarge)
            AddType.entries.forEach { type ->
                OutlinedButton(onClick = { onPick(type) }, modifier = Modifier.fillMaxWidth()) {
                    Text(type.label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = fullDateLabel(date),
        onValueChange = {},
        readOnly = true,
        label = { Text("Дата") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onDateChange(LocalDate.of(y, m + 1, d)) },
                    date.year, date.monthValue - 1, date.dayOfMonth
                ).show()
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InitiatorSelector(selected: Initiator, onSelect: (Initiator) -> Unit) {
    val haptics = LocalHaptics.current
    Column {
        Text("Инициатор", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Initiator.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = selected == entry,
                    onClick = {
                        haptics.perform(HapticEvent.Tap)
                        onSelect(entry)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Initiator.entries.size)
                ) { Text(entry.label) }
            }
        }
    }
}

@Composable
private fun CountStepper(label: String, count: Int, onCountChange: (Int) -> Unit) {
    val haptics = LocalHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = {
            if (count > 0) {
                haptics.perform(HapticEvent.Tap)
                onCountChange(count - 1)
            }
        }) { Icon(Icons.Default.Remove, contentDescription = "Меньше") }
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = {
            haptics.perform(HapticEvent.Tap)
            onCountChange(count + 1)
        }) { Icon(Icons.Default.Add, contentDescription = "Больше") }
    }
}

@Composable
private fun SheetActions(onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = onDismiss) { Text("Отмена") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSave) { Text("Сохранить") }
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
    var date by remember { mutableStateOf(entry?.startDate?.toLocalDateOrNull() ?: initialDate) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (entry == null) "Начало месячных" else "Редактировать запись",
                style = MaterialTheme.typography.titleLarge
            )
            DateField(date) { date = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
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
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var initiator by remember { mutableStateOf(entry?.initiator?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var orgasmCount by remember { mutableStateOf(entry?.orgasmCount ?: 0) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (entry == null) "Близость" else "Редактировать запись", style = MaterialTheme.typography.titleLarge)
            DateField(date) { date = it }
            InitiatorSelector(initiator) { initiator = it }
            CountStepper("Количество оргазмов", orgasmCount) { orgasmCount = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
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
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var initiator by remember { mutableStateOf(entry?.initiator?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var accepted by remember { mutableStateOf(entry?.accepted ?: true) }
    var declineReason by remember { mutableStateOf(entry?.declineReason ?: "") }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (entry == null) "Предложение близости" else "Редактировать запись", style = MaterialTheme.typography.titleLarge)
            DateField(date) { date = it }
            InitiatorSelector(initiator) { initiator = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = accepted, onCheckedChange = {
                    haptics.perform(HapticEvent.Toggle)
                    accepted = it
                })
                Spacer(Modifier.width(8.dp))
                Text(if (accepted) "Принято" else "Отклонено")
            }
            if (!accepted) {
                OutlinedTextField(
                    value = declineReason,
                    onValueChange = { declineReason = it },
                    label = { Text("Причина отказа") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
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
    var date by remember { mutableStateOf(entry?.date?.toLocalDateOrNull() ?: initialDate) }
    var person by remember { mutableStateOf(entry?.person?.let(Initiator::fromStorage) ?: Initiator.ME) }
    var orgasmCount by remember { mutableStateOf(entry?.orgasmCount ?: 0) }
    var notes by remember { mutableStateOf(entry?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(if (entry == null) "Мастурбация" else "Редактировать запись", style = MaterialTheme.typography.titleLarge)
            DateField(date) { date = it }
            InitiatorSelector(person) { person = it }
            CountStepper("Количество оргазмов", orgasmCount) { orgasmCount = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
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
