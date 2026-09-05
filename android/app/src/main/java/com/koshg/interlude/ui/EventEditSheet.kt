package com.koshg.interlude.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.koshg.interlude.data.CalendarEvent
import com.koshg.interlude.data.EVENT_COLOR_PALETTE
import com.koshg.interlude.haptics.HapticEvent
import com.koshg.interlude.haptics.LocalHaptics
import com.koshg.interlude.ui.theme.appColors
import com.koshg.interlude.util.toLocalDateOrNull
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditSheet(
    initialDate: LocalDate,
    event: CalendarEvent?,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val appColors = appColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(event?.title ?: "") }
    // Parsed leniently, like every other sheet: a stored date string is not guaranteed well-formed
    // (an imported JSON file may have been hand-edited), and LocalDate.parse would throw here,
    // taking the app down on a tap that only meant to open an event for editing.
    var date by remember { mutableStateOf(event?.date?.toLocalDateOrNull() ?: initialDate) }
    var allDay by remember { mutableStateOf(event?.allDay ?: false) }
    var startTime by remember { mutableStateOf(event?.startTime ?: "10:00") }
    var endTime by remember { mutableStateOf(event?.endTime ?: "11:00") }
    var color by remember { mutableStateOf(event?.color ?: EVENT_COLOR_PALETTE.first()) }
    var notes by remember { mutableStateOf(event?.notes ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = appColors.warmSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (event == null) "Новое событие" else "Редактировать событие",
                style = MaterialTheme.typography.titleLarge,
                color = appColors.textPrimary
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                shape = sheetFieldShape,
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

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
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = appColors.accent, modifier = Modifier.size(20.dp))
                Text(date.toString(), style = MaterialTheme.typography.bodyLarge, color = appColors.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = appColors.textSecondary, modifier = Modifier.size(18.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = allDay, onCheckedChange = {
                    haptics.perform(HapticEvent.Toggle)
                    allDay = it
                })
                Spacer(Modifier.width(8.dp))
                Text("Весь день", color = appColors.textPrimary)
            }

            if (!allDay) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Начало") },
                        shape = sheetFieldShape,
                        colors = sheetFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { pickTime(context, startTime) { startTime = it } }
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Конец") },
                        shape = sheetFieldShape,
                        colors = sheetFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { pickTime(context, endTime) { endTime = it } }
                    )
                }
            }

            Column {
                Text("Цвет", style = MaterialTheme.typography.labelLarge, color = appColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EVENT_COLOR_PALETTE.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (color == c) 2.dp else 0.dp,
                                    color = appColors.textPrimary,
                                    shape = CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Отмена", color = appColors.textSecondary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(
                                CalendarEvent(
                                    id = event?.id ?: newEventId(),
                                    title = title.trim(),
                                    date = date.toString(),
                                    allDay = allDay,
                                    startTime = if (allDay) null else startTime,
                                    endTime = if (allDay) null else endTime,
                                    color = color,
                                    notes = notes.trim()
                                )
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = Color.White)
                ) { Text("Сохранить") }
            }
        }
    }
}

private fun pickTime(context: Context, current: String, onPicked: (String) -> Unit) {
    val parts = current.split(":").mapNotNull { it.toIntOrNull() }
    val hour = parts.getOrElse(0) { 10 }
    val minute = parts.getOrElse(1) { 0 }
    TimePickerDialog(
        context,
        { _, h, m -> onPicked("%02d:%02d".format(h, m)) },
        hour, minute, true
    ).show()
}
