package com.koshg.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koshg.calendar.data.CalendarEvent
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.ui.theme.colorFor
import com.koshg.calendar.util.CyclePhase
import com.koshg.calendar.util.CycleStats
import com.koshg.calendar.util.WEEKDAY_SHORT_NAMES
import com.koshg.calendar.util.cyclePhaseFor
import com.koshg.calendar.util.monthYearLabel
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** What the "+" FAB is currently editing/creating. Carries the pre-filled date for new entries. */
sealed interface ActiveSheet {
    data class Event(val event: CalendarEvent?, val date: LocalDate) : ActiveSheet
    data class Period(val entry: PeriodEntry?, val date: LocalDate) : ActiveSheet
    data class Sex(val entry: SexEntry?, val date: LocalDate) : ActiveSheet
    data class Proposal(val entry: ProposalEntry?, val date: LocalDate) : ActiveSheet
    data class Masturbation(val entry: MasturbationEntry?, val date: LocalDate) : ActiveSheet

    /** The FAB's "add" flow — a single sheet with a type-chip row instead of a two-step chooser. */
    data class New(val date: LocalDate) : ActiveSheet
}

internal enum class IntimacyMarker { NONE, SEX, PROPOSAL_ACCEPTED, PROPOSAL_DECLINED }

private data class GridDayInfo(val date: LocalDate, val phase: CyclePhase?, val isFuture: Boolean)

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
    var showHistory by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }

    val periodByDate = remember(cycleState.periods) { cycleState.periods.associateBy { it.startDate } }
    val sexByDate = remember(intimacyState.sexEntries) { intimacyState.sexEntries.associateBy { it.date } }
    val proposalByDate = remember(intimacyState.proposalEntries) { intimacyState.proposalEntries.associateBy { it.date } }
    val masturbationDates = remember(intimacyState.masturbationEntries) {
        intimacyState.masturbationEntries.map { it.date }.toSet()
    }

    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticEvent.Select)
                        activeSheet = ActiveSheet.New(uiState.selectedDate)
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
            ) {
                CalendarHeader(
                    stats = cycleState.stats,
                    todayPhase = cyclePhaseFor(LocalDate.now(), cycleState.periods, cycleState.stats.appliedMarginDays),
                    searchActive = searchActive,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onToggleSearch = {
                        haptics.perform(HapticEvent.Tap)
                        searchActive = !searchActive
                        if (!searchActive) viewModel.setSearchQuery("")
                    },
                    onToday = { haptics.perform(HapticEvent.Tap); viewModel.goToToday() },
                    onOpenHistory = {
                        haptics.perform(HapticEvent.Tap)
                        showHistory = true
                    }
                )

                val baseMonth = remember { YearMonth.now() }
                val pagerPageCount = 2401 // ~100 years either side of baseMonth — plenty of headroom
                val pagerCenterPage = pagerPageCount / 2
                val pagerState = rememberPagerState(
                    initialPage = pagerCenterPage + ChronoUnit.MONTHS.between(baseMonth, uiState.viewMonth).toInt()
                ) { pagerPageCount }

                LaunchedEffect(pagerState.currentPage) {
                    val swipedToMonth = baseMonth.plusMonths((pagerState.currentPage - pagerCenterPage).toLong())
                    // This only differs from the current view month on a genuine user swipe --
                    // a chevron-driven change already lands here with swipedToMonth already
                    // matching (the other LaunchedEffect below just animates the pager to catch
                    // up), so a haptic here never doubles up with the chevron's own tap.
                    if (swipedToMonth != uiState.viewMonth) {
                        haptics.perform(HapticEvent.Tap)
                        viewModel.setViewMonth(swipedToMonth)
                    }
                }
                LaunchedEffect(uiState.viewMonth) {
                    val targetPage = pagerCenterPage + ChronoUnit.MONTHS.between(baseMonth, uiState.viewMonth).toInt()
                    if (pagerState.currentPage != targetPage) {
                        pagerState.animateScrollToPage(targetPage)
                    }
                }

                HorizontalPager(state = pagerState) { page ->
                    val month = baseMonth.plusMonths((page - pagerCenterPage).toLong())
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MonthNav(
                            monthLabel = monthYearLabel(month.atDay(1)),
                            onPrev = { haptics.perform(HapticEvent.Tap); viewModel.goToPreviousMonth() },
                            onNext = { haptics.perform(HapticEvent.Tap); viewModel.goToNextMonth() }
                        )
                        Spacer(Modifier.height(8.dp))
                        WeekdayHeader()
                        MonthGrid(
                            viewMonth = month,
                            selectedDate = uiState.selectedDate,
                            eventsByDate = uiState.eventsByDate,
                            periods = cycleState.periods,
                            marginDays = cycleState.stats.appliedMarginDays,
                            sexByDate = sexByDate,
                            proposalByDate = proposalByDate,
                            masturbationDates = masturbationDates,
                            onDayClick = { date ->
                                haptics.perform(HapticEvent.Tap)
                                viewModel.selectDate(date)
                            }
                        )
                    }
                }

                PhaseLegend()

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
    }

    if (showHistory) {
        HistoryScreen(
            periods = cycleState.periods,
            sexEntries = intimacyState.sexEntries,
            proposalEntries = intimacyState.proposalEntries,
            masturbationEntries = intimacyState.masturbationEntries,
            onClose = { showHistory = false }
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

        is ActiveSheet.New -> UnifiedAddSheet(
            initialType = AddType.Period,
            initialDate = sheet.date,
            onDismiss = { activeSheet = null },
            onSavePeriod = {
                haptics.perform(HapticEvent.LogEntry)
                cycleViewModel.savePeriod(it)
                activeSheet = null
            },
            onSaveSex = {
                haptics.perform(HapticEvent.LogEntry)
                intimacyViewModel.saveSexEntry(it)
                activeSheet = null
            },
            onSaveProposal = {
                haptics.perform(HapticEvent.LogEntry)
                intimacyViewModel.saveProposalEntry(it)
                activeSheet = null
            },
            onSaveMasturbation = {
                haptics.perform(HapticEvent.LogEntry)
                intimacyViewModel.saveMasturbationEntry(it)
                activeSheet = null
            }
        )

        null -> Unit
    }
}

@Composable
private fun CalendarHeader(
    stats: CycleStats,
    todayPhase: CyclePhase?,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToday: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val appColors = appColors()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (searchActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Поиск событий…", color = appColors.textSecondary) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        cursorColor = appColors.accent
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть поиск", tint = appColors.textPrimary)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = "История и тренды", tint = appColors.textSecondary)
                }
                Text(
                    text = "КАЛЕНДАРЬ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    color = appColors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToday) {
                    Icon(Icons.Default.Today, contentDescription = "Сегодня", tint = appColors.textSecondary)
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск", tint = appColors.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stats.currentCycleDay?.let { "Цикл, день $it" } ?: "Цикл",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary
                )
                if (todayPhase != null) {
                    Text(
                        text = "  ${todayPhase.label}",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.colorFor(todayPhase),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val subtitle = when {
                stats.predictedNextPeriodEarliest == null || stats.predictedNextPeriodLatest == null ->
                    "Добавьте дату месячных, чтобы увидеть прогноз"
                else -> {
                    val earliestDays = ChronoUnit.DAYS.between(today, stats.predictedNextPeriodEarliest)
                    val latestDays = ChronoUnit.DAYS.between(today, stats.predictedNextPeriodLatest)
                    when {
                        latestDays < 0 -> "Месячные уже наступили"
                        earliestDays <= 0 -> "Ожидаемый день месячных"
                        earliestDays == latestDays -> "Следующие месячные через $earliestDays дн."
                        else -> "Следующие месячные через $earliestDays–$latestDays дн."
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = appColors.textSecondary
            )
            if (stats.isIrregular) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Цикл нерегулярный — точность прогноза по календарю снижена",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.warning
                )
            }
        }
    }
}

@Composable
private fun MonthNav(monthLabel: String, onPrev: () -> Unit, onNext: () -> Unit) {
    val appColors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц", tint = appColors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = monthLabel.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = appColors.textPrimary
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц", tint = appColors.textPrimary)
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val appColors = appColors()
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
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textSecondary
            )
        }
    }
}

@Composable
private fun PhaseLegend() {
    val appColors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CyclePhase.entries.forEach { phase ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(appColors.colorFor(phase))
                )
                Text(
                    text = phase.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = appColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    viewMonth: YearMonth,
    selectedDate: LocalDate,
    eventsByDate: Map<String, List<CalendarEvent>>,
    periods: List<PeriodEntry>,
    marginDays: Int,
    sexByDate: Map<String, SexEntry>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstOfMonth = viewMonth.atDay(1)
    val firstWeekdayIndex = firstOfMonth.dayOfWeek.value - 1 // Monday = 0 .. Sunday = 6
    val gridStart = firstOfMonth.minusDays(firstWeekdayIndex.toLong())
    val totalCells = firstWeekdayIndex + viewMonth.lengthOfMonth()
    val weeks = (totalCells + 6) / 7

    val gridDays = remember(viewMonth, periods, marginDays) {
        (0 until weeks * 7).map { i ->
            val date = gridStart.plusDays(i.toLong())
            GridDayInfo(date, cyclePhaseFor(date, periods, marginDays), date.isAfter(today))
        }
    }

    Column {
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                for (dow in 0 until 7) {
                    val idx = week * 7 + dow
                    val info = gridDays[idx]
                    val prevInfo = if (dow > 0) gridDays[idx - 1] else null
                    val nextInfo = if (dow < 6) gridDays[idx + 1] else null

                    val mergesWithPrev = prevInfo != null &&
                        info.phase != null && info.phase == prevInfo.phase
                    val mergesWithNext = nextInfo != null &&
                        info.phase != null && info.phase == nextInfo.phase

                    if (dow > 0) {
                        Spacer(Modifier.width(if (mergesWithPrev) 0.dp else 3.dp))
                    }

                    val dateKey = info.date.toString()
                    val marker = when {
                        sexByDate.containsKey(dateKey) -> IntimacyMarker.SEX
                        proposalByDate[dateKey]?.accepted == true -> IntimacyMarker.PROPOSAL_ACCEPTED
                        proposalByDate.containsKey(dateKey) -> IntimacyMarker.PROPOSAL_DECLINED
                        else -> IntimacyMarker.NONE
                    }

                    DayCell(
                        date = info.date,
                        inCurrentMonth = YearMonth.from(info.date) == viewMonth,
                        isToday = info.date == today,
                        isSelected = info.date == selectedDate,
                        phase = info.phase,
                        isFuture = info.isFuture,
                        roundStart = !mergesWithPrev,
                        roundEnd = !mergesWithNext,
                        dayEvents = eventsByDate[dateKey].orEmpty(),
                        intimacyMarker = marker,
                        hasMasturbation = dateKey in masturbationDates,
                        onClick = { onDayClick(info.date) },
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
    phase: CyclePhase?,
    isFuture: Boolean,
    roundStart: Boolean,
    roundEnd: Boolean,
    dayEvents: List<CalendarEvent>,
    intimacyMarker: IntimacyMarker,
    hasMasturbation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = appColors()
    // Percent-based (not a fixed dp) so a single cell rounds into a true circle and a merged
    // run gets a proper capsule end-cap, regardless of the row's exact height.
    val round = CornerSize(50)
    val square = CornerSize(0.dp)
    val runShape = RoundedCornerShape(
        topStart = if (roundStart) round else square,
        bottomStart = if (roundStart) round else square,
        topEnd = if (roundEnd) round else square,
        bottomEnd = if (roundEnd) round else square
    )
    val pillShape = RoundedCornerShape(percent = 50)

    val phaseColor = phase?.let { appColors.colorFor(it) }
    // Every day with a known phase fills solid -- upcoming (predicted) days at full strength,
    // since what's coming up is the whole point of a forecast calendar, while already-elapsed
    // days fade back a touch to keep the emphasis forward-looking.
    val monthAlpha = if (inCurrentMonth) 1f else 0.4f
    val contentAlpha = monthAlpha * (if (isFuture) 1f else 0.6f)

    val textColor = when {
        phaseColor != null -> Color.White
        else -> appColors.textPrimary
    }

    val cellModifier = when {
        phaseColor != null -> Modifier
            .clip(runShape)
            .background(phaseColor.copy(alpha = contentAlpha))
        else -> Modifier
            .clip(pillShape)
            .background(appColors.warmSurface.copy(alpha = monthAlpha))
    }

    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .height(38.dp)
            .then(cellModifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 2.dp,
                        color = if (phaseColor != null) Color.White else appColors.accent,
                        shape = if (phaseColor != null) runShape else pillShape
                    )
            )
        }
        // A colored ring around the date -- rather than an icon underneath -- flags what
        // happened that day: sex, an accepted/declined proposal, or masturbation (in that
        // priority order, since at most one ring fits). Calendar-event colors stay as the
        // small dots below, since those are a different, possibly-multi-valued kind of marker.
        val markerColor = when (intimacyMarker) {
            IntimacyMarker.SEX -> appColors.intimacy
            IntimacyMarker.PROPOSAL_ACCEPTED -> appColors.proposalAccepted
            IntimacyMarker.PROPOSAL_DECLINED -> appColors.proposalDeclined
            IntimacyMarker.NONE -> if (hasMasturbation) appColors.solo else null
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // The marker ring always renders at full brightness, regardless of the cell's
                // own past/future/adjacent-month fade -- it flags an actual logged entry, not
                // a prediction, so it should never read as dimmed.
                when {
                    isToday -> DottedRing(color = markerColor ?: textColor, size = 27.dp)
                    markerColor != null -> Box(
                        modifier = Modifier
                            .size(27.dp)
                            .border(2.dp, markerColor, CircleShape)
                    )
                }
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = textColor.copy(alpha = contentAlpha)
                )
            }
            if (dayEvents.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    dayEvents.take(2).forEach { evt ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(evt.color))
                        )
                    }
                }
            }
        }
    }
}
