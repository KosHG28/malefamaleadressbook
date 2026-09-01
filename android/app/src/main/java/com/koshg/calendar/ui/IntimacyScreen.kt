package com.koshg.calendar.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.Initiator
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.util.fullDateLabel
import java.time.LocalDate

private enum class EntryType(val label: String) {
    Sex("Секс"),
    Proposal("Предложение")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntimacyScreen(viewModel: IntimacyViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHaptics.current
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Близость") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                haptics.perform(HapticEvent.Select)
                showAddSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить запись")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("История близости", style = MaterialTheme.typography.titleSmall)
            }
            if (uiState.sexEntries.isEmpty()) {
                item {
                    Text(
                        "Записей пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.sexEntries, key = { "sex-${it.id}" }) { entry ->
                    SexRow(entry = entry, onDelete = {
                        haptics.perform(HapticEvent.Delete)
                        viewModel.deleteSexEntry(entry)
                    })
                }
            }

            item {
                Text(
                    "Предложения",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (uiState.proposalEntries.isEmpty()) {
                item {
                    Text(
                        "Записей пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.proposalEntries, key = { "proposal-${it.id}" }) { entry ->
                    ProposalRow(entry = entry, onDelete = {
                        haptics.perform(HapticEvent.Delete)
                        viewModel.deleteProposalEntry(entry)
                    })
                }
            }
        }
    }

    if (showAddSheet) {
        AddIntimacySheet(
            onDismiss = { showAddSheet = false },
            onSaveSex = { date, initiator, notes ->
                haptics.perform(HapticEvent.LogEntry)
                viewModel.addSexEntry(date, initiator, notes)
                showAddSheet = false
            },
            onSaveProposal = { date, initiator, accepted, notes ->
                haptics.perform(HapticEvent.LogEntry)
                viewModel.addProposalEntry(date, initiator, accepted, notes)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun SexRow(entry: SexEntry, onDelete: () -> Unit) {
    val colors = appColors()
    val date = LocalDate.parse(entry.date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.periodContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = colors.intimacy)
            Column {
                Text(fullDateLabel(date), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Инициатор: ${Initiator.fromStorage(entry.initiator).label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProposalRow(entry: ProposalEntry, onDelete: () -> Unit) {
    val colors = appColors()
    val date = LocalDate.parse(entry.date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (entry.accepted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (entry.accepted) colors.fertile else MaterialTheme.colorScheme.error
            )
            Column {
                Text(fullDateLabel(date), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "От: ${Initiator.fromStorage(entry.initiator).label} · ${if (entry.accepted) "принято" else "отклонено"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIntimacySheet(
    onDismiss: () -> Unit,
    onSaveSex: (LocalDate, Initiator, String) -> Unit,
    onSaveProposal: (LocalDate, Initiator, Boolean, String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHaptics.current

    var type by remember { mutableStateOf(EntryType.Sex) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var initiator by remember { mutableStateOf(Initiator.ME) }
    var accepted by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Новая запись", style = MaterialTheme.typography.titleLarge)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EntryType.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = type == entry,
                        onClick = {
                            haptics.perform(HapticEvent.Tap)
                            type = entry
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = EntryType.entries.size)
                    ) { Text(entry.label) }
                }
            }

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
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).show()
                    }
            )

            Column {
                Text("Инициатор", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Initiator.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = initiator == entry,
                            onClick = {
                                haptics.perform(HapticEvent.Tap)
                                initiator = entry
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Initiator.entries.size)
                        ) { Text(entry.label) }
                    }
                }
            }

            if (type == EntryType.Proposal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = accepted, onCheckedChange = {
                        haptics.perform(HapticEvent.Toggle)
                        accepted = it
                    })
                    Spacer(Modifier.width(8.dp))
                    Text(if (accepted) "Принято" else "Отклонено")
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    when (type) {
                        EntryType.Sex -> onSaveSex(date, initiator, notes)
                        EntryType.Proposal -> onSaveProposal(date, initiator, accepted, notes)
                    }
                }) { Text("Сохранить") }
            }
        }
    }
}
