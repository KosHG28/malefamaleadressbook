package com.koshg.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.unit.dp
import com.koshg.calendar.data.CalendarEvent
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.util.CycleStats
import com.koshg.calendar.util.WEEKDAY_SHORT_NAMES
import com.koshg.calendar.util.isFertileDay
import com.koshg.calendar.util.isLoggedPeriodDay
import com.koshg.calendar.util.isOvulationDay
import com.koshg.calendar.util.isPredictedPeriodDay
import com.koshg.calendar.util.monthYearLabel
import java.time.LocalDate
import java.time.YearMonth

/** What the "+" FAB is currently editing/creating. Carries the pre-filled date for new entries. */
sealed interface ActiveSheet {
    data class Event(val event: CalendarEvent?, val date: LocalDate) : ActiveSheet
    data class Period(val entry: PeriodEntry?, val date: LocalDate) : ActiveSheet
    data class Sex(val entry: SexEntry?, val date: LocalDate) : ActiveSheet
    data class Proposal(val entry: ProposalEntry?, val date: LocalDate) : ActiveSheet
    data class Masturbation(val entry: MasturbationEntry?, val date: LocalDate) : ActiveSheet
}

internal enum class IntimacyMarker { NONE, SEX, PROPOSAL_ACCEPTED, PROPOSAL_DECLINED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    cycleViewModel: CycleViewModel,
    intimacyViewModel: IntimacyViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val cycleState by cycleViewModel.uiState.collectAsState()
    val intimacyState by intimacyViewModel.uiState.collectAsState()
    val haptics = LocalHaptics.current
    val appColors = appColors()

    var searchActive by remember { mutableStateOf(false) }
    var showAddChooser by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }

    val periodByDate = remember(cycleState.periods) { cycleState.periods.associateBy { it.startDate } }
    val sexByDate = remember(intimacyState.sexEntries) { intimacyState.sexEntries.associateBy { it.date } }
    val proposalByDate = remember(intimacyState.proposalEntries) { intimacyState.proposalEntries.associateBy { it.date } }
    val masturbationDates = remember(intimacyState.masturbationEntries) {
        intimacyState.masturbationEntries.map { it.date }.toSet()
    }

    Scaffold(
        containerColor = appColors.warmBackground,
        topBar = {
            CalendarHeader(
                monthLabel = monthYearLabel(uiState.viewMonth.atDay(1)),
                searchActive = searchActive,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::setSearchQuery,
                onToggleSearch = {
                    haptics.perform(HapticEvent.Tap)
                    searchActive = !searchActive
                    if (!searchActive) viewModel.setSearchQuery("")
                },
                onPrevMonth = { haptics.perform(HapticEvent.Tap); viewModel.goToPreviousMonth() },
                onNextMonth = { haptics.perform(HapticEvent.Tap); viewModel.goToNextMonth() },
                onToday = { haptics.perform(HapticEvent.Tap); viewModel.goToToday() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.perform(HapticEvent.Select)
                    showAddChooser = true
                },
                containerColor = appColors.accent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(appColors.warmBackground)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                WeekdayHeader()
                MonthGrid(
                    viewMonth = uiState.viewMonth,
                    selectedDate = uiState.selectedDate,
                    eventsByDate = uiState.eventsByDate,
                    periods = cycleState.periods,
                    cycleStats = cycleState.stats,
                    sexByDate = sexByDate,
                    proposalByDate = proposalByDate,
                    masturbationDates = masturbationDates,
                    onDayClick = { date ->
                        haptics.perform(HapticEvent.Tap)
                        viewModel.selectDate(date)
                    }
                )
            }

            CycleStatsCard(cycleState.stats, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            DayAgendaPanel(
                selectedDate = uiState.selectedDate,
                events = uiState.selectedDateEvents,
                periodEntry = periodByDate[uiState.selectedDate.toString()],
                sexEntry = sexByDate[uiState.selectedDate.toString()],
                proposalEntry = proposalByDate[uiState.selectedDate.toString()],
                masturbationEntries = intimacyState.masturbationEntries.filter { it.date == uiState.selectedDate.toString() },
                onEventClick = { activeSheet = ActiveSheet.Event(it, uiState.selectedDate) },
                onPeriodClick = { activeSheet = ActiveSheet.Period(it, uiState.selectedDate) },
                onSexClick = { activeSheet = ActiveSheet.Sex(it, uiState.selectedDate) },
                onProposalClick = { activeSheet = ActiveSheet.Proposal(it, uiState.selectedDate) },
                onMasturbationClick = { activeSheet = ActiveSheet.Masturbation(it, uiState.selectedDate) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showAddChooser) {
        AddChooserSheet(
            onDismiss = { showAddChooser = false },
            onPick = { type ->
                showAddChooser = false
                val date = uiState.selectedDate
                activeSheet = when (type) {
                    AddType.Event -> ActiveSheet.Event(null, date)
                    AddType.Period -> ActiveSheet.Period(null, date)
                    AddType.Sex -> ActiveSheet.Sex(null, date)
                    AddType.Proposal -> ActiveSheet.Proposal(null, date)
                    AddType.Masturbation -> ActiveSheet.Masturbation(null, date)
                }
            }
        )
    }

    when (val sheet = activeSheet) {
        is ActiveSheet.Event -> EventEditSheet(
            initialDate = sheet.date,
            event = sheet.event,
            onDismiss = { activeSheet = null },
            onSave = { event ->
                haptics.perform(HapticEvent.Confirm)
                viewModel.saveEvent(event)
                activeSheet = null
            },
            onDelete = sheet.event?.let {
                {
                    haptics.perform(HapticEvent.Delete)
                    viewModel.deleteEvent(it)
                    activeSheet = null
                }
            }
        )

        is ActiveSheet.Period -> PeriodSheet(
            initialDate = sheet.date,
            entry = sheet.entry,
            onDismiss = { activeSheet = null },
            onSave = {
                haptics.perform(HapticEvent.LogEntry)
                cycleViewModel.savePeriod(it)
                activeSheet = null
            },
            onDelete = sheet.entry?.let {
                {
                    haptics.perform(HapticEvent.Delete)
                    cycleViewModel.deletePeriod(it)
                    activeSheet = null
                }
            }
        )

        is ActiveSheet.Sex -> SexSheet(
            initialDate = sheet.date,
            entry = sheet.entry,
            onDismiss = { activeSheet = null },
            onSave = {
                haptics.perform(HapticEvent.LogEntry)
                intimacyViewModel.saveSexEntry(it)
                activeSheet = null
            },
            onDelete = sheet.entry?.let {
                {
                    haptics.perform(HapticEvent.Delete)
                    intimacyViewModel.deleteSexEntry(it)
                    activeSheet = null
                }
            }
        )

        is ActiveSheet.Proposal -> ProposalSheet(
            initialDate = sheet.date,
            entry = sheet.entry,
            onDismiss = { activeSheet = null },
            onSave = {
                haptics.perform(HapticEvent.LogEntry)
                intimacyViewModel.saveProposalEntry(it)
                activeSheet = null
            },
            onDelete = sheet.entry?.let {
                {
                    haptics.perform(HapticEvent.Delete)
                    intimacyViewModel.deleteProposalEntry(it)
                    activeSheet = null
                }
            }
        )

        is ActiveSheet.Masturbation -> MasturbationSheet(
            initialDate = sheet.date,
            entry = sheet.entry,
            onDismiss = { activeSheet = null },
            onSave = {
                haptics.perform(HapticEvent.LogEntry)
                intimacyViewModel.saveMasturbationEntry(it)
                activeSheet = null
            },
            onDelete = sheet.entry?.let {
                {
                    haptics.perform(HapticEvent.Delete)
                    intimacyViewModel.deleteMasturbationEntry(it)
                    activeSheet = null
                }
            }
        )

        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarHeader(
    monthLabel: String,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit
) {
    val appColors = appColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.warmBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (searchActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть поиск")
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToday) {
                    Icon(Icons.Default.Today, contentDescription = "Сегодня")
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц")
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        WEEKDAY_SHORT_NAMES.forEach { day ->
            Text(
                text = day.uppercase(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
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
    periods: List<PeriodEntry>,
    cycleStats: CycleStats,
    sexByDate: Map<String, SexEntry>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>,
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
                    val dateKey = date.toString()
                    val marker = when {
                        sexByDate.containsKey(dateKey) -> IntimacyMarker.SEX
                        proposalByDate[dateKey]?.accepted == true -> IntimacyMarker.PROPOSAL_ACCEPTED
                        proposalByDate.containsKey(dateKey) -> IntimacyMarker.PROPOSAL_DECLINED
                        else -> IntimacyMarker.NONE
                    }
                    DayCell(
                        date = date,
                        inCurrentMonth = YearMonth.from(date) == viewMonth,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        dayEvents = eventsByDate[dateKey].orEmpty(),
                        isPeriodDay = isLoggedPeriodDay(periods, date),
                        isPredictedPeriodDay = isPredictedPeriodDay(cycleStats, date),
                        isFertileDay = isFertileDay(cycleStats, date),
                        isOvulationDay = isOvulationDay(cycleStats, date),
                        intimacyMarker = marker,
                        hasMasturbation = dateKey in masturbationDates,
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
    isPeriodDay: Boolean,
    isPredictedPeriodDay: Boolean,
    isFertileDay: Boolean,
    isOvulationDay: Boolean,
    intimacyMarker: IntimacyMarker,
    hasMasturbation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = appColors()
    val background = when {
        isPeriodDay -> appColors.periodContainer
        isFertileDay -> appColors.fertileContainer
        else -> appColors.warmSurface
    }
    val borderColor = when {
        isSelected -> appColors.accent
        isToday -> MaterialTheme.colorScheme.primary
        isPredictedPeriodDay -> appColors.period.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val contentAlpha = if (inCurrentMonth) 1f else 0.35f

    Column(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isOvulationDay) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(appColors.ovulation.copy(alpha = 0.35f))
                )
            }
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isOvulationDay) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            when (intimacyMarker) {
                IntimacyMarker.SEX -> Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Была близость",
                    tint = appColors.intimacy,
                    modifier = Modifier.size(13.dp)
                )
                IntimacyMarker.PROPOSAL_ACCEPTED -> Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = "Предложение принято",
                    tint = appColors.proposalAccepted,
                    modifier = Modifier.size(13.dp)
                )
                IntimacyMarker.PROPOSAL_DECLINED -> Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = "Предложение отклонено",
                    tint = appColors.proposalDeclined,
                    modifier = Modifier.size(13.dp)
                )
                IntimacyMarker.NONE -> Unit
            }
            if (hasMasturbation) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(appColors.solo)
                )
            }
            dayEvents.take(2).forEach { evt ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(evt.color))
                )
            }
        }
        if (dayEvents.size > 2) {
            Text(
                text = "+${dayEvents.size - 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
