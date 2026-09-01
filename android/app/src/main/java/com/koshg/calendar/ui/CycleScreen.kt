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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.util.fullDateLabel
import com.koshg.calendar.util.shortDateLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(viewModel: CycleViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHaptics.current
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Цикл") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                haptics.perform(HapticEvent.Select)
                showAddSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить месячные")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { CycleStatsCard(uiState) }
            item {
                Text(
                    text = "История",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (uiState.periods.isEmpty()) {
                item {
                    Text(
                        text = "Записей пока нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.periods, key = { it.id }) { entry ->
                    PeriodHistoryRow(
                        entry = entry,
                        onDelete = {
                            haptics.perform(HapticEvent.Delete)
                            viewModel.deletePeriod(entry)
                        }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddPeriodSheet(
            onDismiss = { showAddSheet = false },
            onSave = { date, notes ->
                haptics.perform(HapticEvent.LogEntry)
                viewModel.addPeriod(date, notes)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun CycleStatsCard(uiState: CycleUiState) {
    val colors = appColors()
    val stats = uiState.stats

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.periodContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (stats.latestPeriodStart == null) {
                Text(
                    text = "Пока нет данных. Добавьте дату начала последних месячных, чтобы увидеть прогноз овуляции и фертильного окна.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                StatRow("День цикла", stats.currentCycleDay?.toString() ?: "—")
                StatRow("Последние месячные", shortDateLabel(stats.latestPeriodStart))
                StatRow(
                    "Средняя длина цикла",
                    if (stats.cycleHistoryCount > 0) {
                        "${stats.averageCycleLengthDays} дн. (по ${stats.cycleHistoryCount} циклам)"
                    } else {
                        "${stats.averageCycleLengthDays} дн. (по умолчанию)"
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                stats.predictedOvulation?.let { StatRow("Овуляция (прогноз)", shortDateLabel(it), colors.ovulation) }
                if (stats.fertileWindowStart != null && stats.fertileWindowEnd != null) {
                    StatRow(
                        "Фертильное окно",
                        "${shortDateLabel(stats.fertileWindowStart)} – ${shortDateLabel(stats.fertileWindowEnd)}",
                        colors.fertile
                    )
                }
                stats.predictedNextPeriod?.let { StatRow("Следующие месячные (прогноз)", shortDateLabel(it), colors.period) }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, accent: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PeriodHistoryRow(entry: PeriodEntry, onDelete: () -> Unit) {
    val date = LocalDate.parse(entry.startDate)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(fullDateLabel(date), style = MaterialTheme.typography.bodyLarge)
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeriodSheet(onDismiss: () -> Unit, onSave: (LocalDate, String) -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(LocalDate.now()) }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Начало месячных", style = MaterialTheme.typography.titleLarge)

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
                Button(onClick = { onSave(date, notes) }) { Text("Сохранить") }
            }
        }
    }
}
