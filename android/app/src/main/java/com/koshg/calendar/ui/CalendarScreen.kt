package com.koshg.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.CalendarEvent
import com.koshg.calendar.util.WEEKDAY_SHORT_NAMES
import com.koshg.calendar.util.dayAgendaLabel
import com.koshg.calendar.util.monthYearLabel
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showAddNewFor by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            CalendarTopBar(
                monthLabel = monthYearLabel(uiState.viewMonth.atDay(1)),
                searchActive = searchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::setSearchQuery,
                onToggleSearch = {
                    searchActive = !searchActive
                    if (!searchActive) viewModel.setSearchQuery("")
                },
                onPrevMonth = viewModel::goToPreviousMonth,
                onNextMonth = viewModel::goToNextMonth,
                onToday = viewModel::goToToday
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddNewFor = uiState.selectedDate }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить событие")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            WeekdayHeader()
            MonthGrid(
                viewMonth = uiState.viewMonth,
                selectedDate = uiState.selectedDate,
                eventsByDate = uiState.eventsByDate,
                onDayClick = viewModel::selectDate
            )
            HorizontalDivider()
            AgendaList(
                label = dayAgendaLabel(uiState.selectedDate),
                events = uiState.selectedDateEvents,
                onEventClick = { editingEvent = it },
                modifier = Modifier.weight(1f)
            )
        }
    }

    showAddNewFor?.let { date ->
        EventEditSheet(
            initialDate = date,
            event = null,
            onDismiss = { showAddNewFor = null },
            onSave = { event ->
                viewModel.saveEvent(event)
                showAddNewFor = null
            },
            onDelete = null
        )
    }

    editingEvent?.let { event ->
        EventEditSheet(
            initialDate = LocalDate.parse(event.date),
            event = event,
            onDismiss = { editingEvent = null },
            onSave = { updated ->
                viewModel.saveEvent(updated)
                editingEvent = null
            },
            onDelete = {
                viewModel.deleteEvent(event)
                editingEvent = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    monthLabel: String,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit
) {
    TopAppBar(
        title = {
            if (searchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Поиск событий…") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц")
                    }
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц")
                    }
                }
            }
        },
        actions = {
            if (searchActive) {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть поиск")
                }
            } else {
                IconButton(onClick = onToday) {
                    Icon(Icons.Default.Today, contentDescription = "Сегодня")
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
            }
        }
    )
}

@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        WEEKDAY_SHORT_NAMES.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    viewMonth: YearMonth,
    selectedDate: LocalDate,
    eventsByDate: Map<String, List<CalendarEvent>>,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = viewMonth.atDay(1)
    val firstWeekdayIndex = firstOfMonth.dayOfWeek.value - 1 // Monday = 0 .. Sunday = 6
    val gridStart = firstOfMonth.minusDays(firstWeekdayIndex.toLong())
    val today = LocalDate.now()

    Column {
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    DayCell(
                        date = date,
                        inCurrentMonth = YearMonth.from(date) == viewMonth,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        dayEvents = eventsByDate[date.toString()].orEmpty(),
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    dayEvents: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val borderColor = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentAlpha = if (inCurrentMonth) 1f else 0.35f

    Column(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dayEvents.take(3).forEach { evt ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(evt.color))
                )
            }
        }
        if (dayEvents.size > 3) {
            Text(
                text = "+${dayEvents.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AgendaList(
    label: String,
    events: List<CalendarEvent>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (events.isEmpty()) {
            Text(
                text = "На этот день событий нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    AgendaItem(event = event, onClick = { onEventClick(event) })
                }
            }
        }
    }
}

@Composable
private fun AgendaItem(event: CalendarEvent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(event.color))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            val timeLabel = if (event.allDay) {
                "Весь день"
            } else {
                listOfNotNull(event.startTime, event.endTime).joinToString(" – ")
            }
            if (timeLabel.isNotBlank()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.notes.isNotBlank()) {
                Text(
                    text = event.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
