package com.koshg.calendar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koshg.calendar.data.CalendarEvent
import com.koshg.calendar.data.DataSnapshot
import com.koshg.calendar.data.MasturbationEntry
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import com.koshg.calendar.data.parseDataSnapshot
import com.koshg.calendar.data.toExportJson
import com.koshg.calendar.haptics.HapticEvent
import com.koshg.calendar.haptics.Haptics
import com.koshg.calendar.haptics.LocalHaptics
import com.koshg.calendar.settings.PhaseFillStyle
import com.koshg.calendar.ui.theme.LocalPalette
import com.koshg.calendar.ui.theme.LocalThemeMode
import com.koshg.calendar.ui.theme.adaptiveAccent
import com.koshg.calendar.ui.theme.appColors
import com.koshg.calendar.ui.theme.phaseColor
import com.koshg.calendar.util.CyclePhase
import com.koshg.calendar.util.CycleStats
import com.koshg.calendar.util.ProactiveSuggestion
import com.koshg.calendar.util.WEEKDAY_SHORT_NAMES
import com.koshg.calendar.util.computeProactiveSuggestion
import com.koshg.calendar.util.cyclePhaseFor
import com.koshg.calendar.util.ovulationDateFor
import com.koshg.calendar.util.cyclePhaseProgressFor
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

private data class GridDayInfo(
    val date: LocalDate,
    val phase: CyclePhase?,
    val isFuture: Boolean,
    val isOvulationDay: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    cycleViewModel: CycleViewModel,
    intimacyViewModel: IntimacyViewModel
) {
    val cycleState by cycleViewModel.uiState.collectAsState()

    // Everything below reads its colors via appColors(), which resolves the current palette/theme
    // mode from these CompositionLocals -- providing them once here, rather than threading
    // parameters through every screen/sheet, lets picking a new scheme or light/dark override in
    // Settings repaint the whole app.
    CompositionLocalProvider(
        LocalPalette provides cycleState.palette,
        LocalThemeMode provides cycleState.themeMode
    ) {
        CalendarScreenContent(viewModel, cycleViewModel, intimacyViewModel, cycleState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreenContent(
    viewModel: CalendarViewModel,
    cycleViewModel: CycleViewModel,
    intimacyViewModel: IntimacyViewModel,
    cycleState: CycleUiState
) {
    val uiState by viewModel.uiState.collectAsState()
    val intimacyState by intimacyViewModel.uiState.collectAsState()
    val haptics = LocalHaptics.current
    val appColors = appColors()
    val context = LocalContext.current

    // Manual export/import (Settings' "Данные" section) -- a plain JSON file the user picks a
    // destination/source for via the system document picker, independent of Android's own Auto
    // Backup: it's on-demand, portable to another device without the same Google account, and
    // readable/editable outside the app.
    val exportDataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = DataSnapshot(
            periods = cycleState.periods,
            events = uiState.events,
            sexEntries = intimacyState.sexEntries,
            proposalEntries = intimacyState.proposalEntries,
            masturbationEntries = intimacyState.masturbationEntries
        )
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(snapshot.toExportJson().toByteArray()) }
        }
    }
    val importDataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching
            val snapshot = parseDataSnapshot(json)
            snapshot.periods.forEach(cycleViewModel::savePeriod)
            snapshot.events.forEach(viewModel::saveEvent)
            snapshot.sexEntries.forEach(intimacyViewModel::saveSexEntry)
            snapshot.proposalEntries.forEach(intimacyViewModel::saveProposalEntry)
            snapshot.masturbationEntries.forEach(intimacyViewModel::saveMasturbationEntry)
        }
    }

    // Turning reminders on requests POST_NOTIFICATIONS first -- setRemindersEnabled(true) only
    // actually schedules the worker once CyclePreferences double-checks the permission is granted,
    // but requesting it here (rather than leaving the toggle on with a silently no-op worker) is
    // what makes the OS permission prompt appear at all.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cycleViewModel.setRemindersEnabled(granted)
    }
    val onRemindersEnabledChange: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            cycleViewModel.setRemindersEnabled(false)
        }
    }

    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showYearOverview by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    // Captured from the FAB's own layout so the add-entry dialog can grow out from wherever the
    // "+" actually sits on screen instead of just appearing centered.
    var fabOrigin by remember { mutableStateOf(Offset.Unspecified) }

    val periodByDate = remember(cycleState.periods) { cycleState.periods.associateBy { it.startDate } }
    val sexByDate = remember(intimacyState.sexEntries) { intimacyState.sexEntries.associateBy { it.date } }
    val proposalByDate = remember(intimacyState.proposalEntries) { intimacyState.proposalEntries.associateBy { it.date } }
    val masturbationDates = remember(intimacyState.masturbationEntries) {
        intimacyState.masturbationEntries.map { it.date }.toSet()
    }
    // Only sex entries get the star -- masturbation's own orgasm count is already visible via its
    // marker ring and the day-agenda row, and doesn't need the same calendar-wide star treatment.
    val orgasmDates = remember(intimacyState.sexEntries) {
        intimacyState.sexEntries.filter { it.orgasmCount > 0 }.map { it.date }.toSet()
    }

    // The FAB guesses intent from the selected day rather than always showing a plain "+":
    // a droplet flags a day with no period entry yet whose start is the model's own next-period
    // prediction, since that's the single most likely thing the user is about to log there.
    val selectedDateKey = uiState.selectedDate.toString()
    val isPredictedPeriodStartDay = !periodByDate.containsKey(selectedDateKey) &&
        cycleState.stats.predictedNextPeriod == uiState.selectedDate
    val fabIcon = if (isPredictedPeriodStartDay) Icons.Default.WaterDrop else Icons.Default.Add
    val fabContentDescription = if (isPredictedPeriodStartDay) {
        "Добавить: прогнозируется начало месячных"
    } else {
        "Добавить"
    }

    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

    // "Adaptive theme" blends the accent across cycle phases instead of a fixed color -- computed
    // once here and threaded down to the FAB and the calendar's own selected-day ring, rather than
    // touching every accent-colored element in the app (dialogs/sheets keep the static accent).
    val dynamicAccent = if (cycleState.adaptiveTheme) {
        appColors.adaptiveAccent(
            cyclePhaseProgressFor(LocalDate.now(), cycleState.periods, cycleState.stats.appliedMarginDays, cycleState.lutealPhaseDays)
        )
    } else {
        appColors.accent
    }

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                val fabInteractionSource = remember { MutableInteractionSource() }
                val fabPressed by fabInteractionSource.collectIsPressedAsState()
                val fabScale by animateFloatAsState(if (fabPressed) 0.9f else 1f, label = "fabScale")
                FloatingActionButton(
                    onClick = {
                        haptics.perform(HapticEvent.Select)
                        activeSheet = ActiveSheet.New(uiState.selectedDate)
                    },
                    containerColor = dynamicAccent,
                    contentColor = Color.White,
                    interactionSource = fabInteractionSource,
                    modifier = Modifier
                        .scale(fabScale)
                        .onGloballyPositioned { fabOrigin = it.boundsInRoot().center }
                ) {
                    AnimatedContent(targetState = fabIcon, label = "fabIcon") { icon ->
                        Icon(icon, contentDescription = fabContentDescription)
                    }
                }
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                val widthClass = windowWidthClassOf(maxWidth.value.toInt())
                val selectedDatePhase = cyclePhaseFor(
                    uiState.selectedDate,
                    cycleState.periods,
                    cycleState.stats.appliedMarginDays,
                    cycleState.lutealPhaseDays
                )
                val agendaPanel: @Composable (Modifier) -> Unit = { agendaModifier ->
                    DayAgendaPanel(
                        selectedDate = uiState.selectedDate,
                        phase = selectedDatePhase,
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
                        modifier = agendaModifier
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    CalendarHeader(
                        stats = cycleState.stats,
                        todayPhase = cyclePhaseFor(
                            LocalDate.now(),
                            cycleState.periods,
                            cycleState.stats.appliedMarginDays,
                            cycleState.lutealPhaseDays
                        ),
                        onToday = { haptics.perform(HapticEvent.Tap); viewModel.goToToday() },
                        onOpenHistory = {
                            haptics.perform(HapticEvent.Tap)
                            showHistory = true
                        },
                        onOpenSettings = {
                            haptics.perform(HapticEvent.Tap)
                            showSettings = true
                        }
                    )

                    if (widthClass == WindowWidthClass.COMPACT) {
                        // A phone, folded or not -- the month grid and the selected day's agenda
                        // stack vertically exactly as before, agenda taking whatever's left.
                        CalendarMonthSection(
                            viewModel = viewModel,
                            cycleViewModel = cycleViewModel,
                            uiState = uiState,
                            cycleState = cycleState,
                            intimacyState = intimacyState,
                            dynamicAccent = dynamicAccent,
                            sexByDate = sexByDate,
                            proposalByDate = proposalByDate,
                            masturbationDates = masturbationDates,
                            orgasmDates = orgasmDates,
                            haptics = haptics,
                            onNewEntry = { activeSheet = ActiveSheet.New(it) }
                        )
                        agendaPanel(Modifier.weight(1f))
                    } else {
                        // An unfolded Fold or a tablet: width to spare, so the grid and the
                        // selected day's agenda sit side by side instead of stacked -- switching
                        // days no longer means scrolling away from the calendar to see it.
                        Row(modifier = Modifier.fillMaxSize()) {
                            CalendarMonthSection(
                                viewModel = viewModel,
                                cycleViewModel = cycleViewModel,
                                uiState = uiState,
                                cycleState = cycleState,
                                intimacyState = intimacyState,
                                dynamicAccent = dynamicAccent,
                                sexByDate = sexByDate,
                                proposalByDate = proposalByDate,
                                masturbationDates = masturbationDates,
                                orgasmDates = orgasmDates,
                                haptics = haptics,
                                onNewEntry = { activeSheet = ActiveSheet.New(it) },
                                modifier = Modifier
                                    .weight(0.55f)
                                    .fillMaxHeight()
                                    .widthIn(max = 480.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                            agendaPanel(Modifier.weight(0.45f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }

    if (showHistory) {
        HistoryScreen(
            periods = cycleState.periods,
            sexEntries = intimacyState.sexEntries,
            proposalEntries = intimacyState.proposalEntries,
            masturbationEntries = intimacyState.masturbationEntries,
            isIrregular = cycleState.stats.isIrregular,
            onClose = { showHistory = false },
            onOpenYearOverview = {
                showHistory = false
                showYearOverview = true
            }
        )
    }

    if (showYearOverview) {
        YearOverviewScreen(
            initialYear = uiState.viewMonth.year,
            periods = cycleState.periods,
            marginDays = cycleState.stats.appliedMarginDays,
            lutealPhaseDays = cycleState.lutealPhaseDays,
            onClose = { showYearOverview = false },
            onMonthClick = { month ->
                viewModel.setViewMonth(month)
                showYearOverview = false
            }
        )
    }

    if (showSettings) {
        SettingsScreen(
            lutealPhaseDays = cycleState.lutealPhaseDays,
            onLutealPhaseDaysChange = cycleViewModel::setLutealPhaseDays,
            adaptiveTheme = cycleState.adaptiveTheme,
            onAdaptiveThemeChange = cycleViewModel::setAdaptiveTheme,
            phaseFillStyle = cycleState.phaseFillStyle,
            onPhaseFillStyleChange = cycleViewModel::setPhaseFillStyle,
            palette = cycleState.palette,
            onPaletteChange = cycleViewModel::setPalette,
            suggestionsEnabled = cycleState.suggestionsEnabled,
            onSuggestionsEnabledChange = cycleViewModel::setSuggestionsEnabled,
            themeMode = cycleState.themeMode,
            onThemeModeChange = cycleViewModel::setThemeMode,
            onExportData = {
                haptics.perform(HapticEvent.Tap)
                exportDataLauncher.launch("calendar-backup-${LocalDate.now()}.json")
            },
            onImportData = {
                haptics.perform(HapticEvent.Tap)
                importDataLauncher.launch(arrayOf("application/json"))
            },
            remindersEnabled = cycleState.remindersEnabled,
            onRemindersEnabledChange = onRemindersEnabledChange,
            onClose = { showSettings = false }
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
            fabOrigin = fabOrigin,
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

/**
 * The month pager/grid, phase legend and proactive-suggestion banner -- everything above the
 * selected day's agenda. Pulled out of [CalendarScreenContent] so it can be placed either above
 * [DayAgendaPanel] (stacked, on a phone) or beside it (two-pane, on an unfolded Fold or tablet)
 * without duplicating the pager/grid wiring for each layout.
 */
@Composable
private fun CalendarMonthSection(
    viewModel: CalendarViewModel,
    cycleViewModel: CycleViewModel,
    uiState: CalendarUiState,
    cycleState: CycleUiState,
    intimacyState: IntimacyUiState,
    dynamicAccent: Color,
    sexByDate: Map<String, SexEntry>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>,
    orgasmDates: Set<String>,
    haptics: Haptics,
    onNewEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
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
                    lutealPhaseDays = cycleState.lutealPhaseDays,
                    phaseFillStyle = cycleState.phaseFillStyle,
                    accentColor = dynamicAccent,
                    sexByDate = sexByDate,
                    proposalByDate = proposalByDate,
                    masturbationDates = masturbationDates,
                    orgasmDates = orgasmDates,
                    onDayClick = { date ->
                        haptics.perform(HapticEvent.Tap)
                        viewModel.selectDate(date)
                    },
                    onDayLongClick = { date ->
                        haptics.perform(HapticEvent.Select)
                        viewModel.selectDate(date)
                        onNewEntry(date)
                    }
                )
            }
        }

        PhaseLegend()

        val proactiveSuggestion = remember(
            intimacyState.sexEntries,
            intimacyState.masturbationEntries,
            intimacyState.proposalEntries,
            cycleState.suggestionsEnabled,
            cycleState.suggestionDismissedUntilEpochDay
        ) {
            when {
                !cycleState.suggestionsEnabled -> null
                LocalDate.now().toEpochDay() < cycleState.suggestionDismissedUntilEpochDay -> null
                else -> computeProactiveSuggestion(
                    intimacyState.sexEntries,
                    intimacyState.masturbationEntries,
                    intimacyState.proposalEntries
                )
            }
        }
        // Keeps rendering the last non-null suggestion while AnimatedVisibility shrinks it
        // away, since proactiveSuggestion itself already flips to null the moment
        // visible does -- without this the banner would vanish instantly instead of
        // collapsing.
        val displayedSuggestion = remember { mutableStateOf<ProactiveSuggestion?>(null) }
        if (proactiveSuggestion != null) displayedSuggestion.value = proactiveSuggestion

        AnimatedVisibility(
            visible = proactiveSuggestion != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            displayedSuggestion.value?.let { suggestion ->
                SuggestionBanner(
                    suggestion = suggestion,
                    onDismiss = { haptics.perform(HapticEvent.Tap); cycleViewModel.dismissSuggestion() }
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    stats: CycleStats,
    todayPhase: CyclePhase?,
    onToday: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val appColors = appColors()
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Default.History, contentDescription = "История и тренды", tint = appColors.textSecondary)
            }
            Text(
                text = "INTERLUDE",
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
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = appColors.textSecondary)
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
                    color = appColors.phaseColor(todayPhase),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row-major 2-column grid (2x2 for the four phases), each cell equal-width, so dots and
        // labels line up on a consistent grid regardless of how long each phase's name is.
        CyclePhase.entries.chunked(2).forEach { rowPhases ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowPhases.forEach { phase ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(appColors.phaseColor(phase))
                        )
                        Text(
                            text = phase.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors.textSecondary
                        )
                    }
                    if (rowPhases.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** A dismissible, non-alarming nudge card -- see [computeProactiveSuggestion] for the triggers. */
@Composable
private fun SuggestionBanner(suggestion: ProactiveSuggestion, onDismiss: () -> Unit) {
    val appColors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 4.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.warmSurface.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Spa,
            contentDescription = null,
            tint = appColors.accent,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                suggestion.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary
            )
            Text(
                suggestion.message,
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textSecondary
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Скрыть подсказку",
                tint = appColors.textSecondary,
                modifier = Modifier.size(16.dp)
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
    marginDays: Int,
    lutealPhaseDays: Int,
    phaseFillStyle: PhaseFillStyle,
    accentColor: Color,
    sexByDate: Map<String, SexEntry>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>,
    orgasmDates: Set<String>,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstOfMonth = viewMonth.atDay(1)
    val firstWeekdayIndex = firstOfMonth.dayOfWeek.value - 1 // Monday = 0 .. Sunday = 6
    val gridStart = firstOfMonth.minusDays(firstWeekdayIndex.toLong())
    // Always 6 weeks, even though a month only strictly needs 5 sometimes -- a fixed row
    // count keeps every page the same height, so swiping between months doesn't visibly
    // resize the grid.
    val weeks = 6

    val gridDays = remember(viewMonth, periods, marginDays, lutealPhaseDays) {
        (0 until weeks * 7).map { i ->
            val date = gridStart.plusDays(i.toLong())
            val phase = cyclePhaseFor(date, periods, marginDays, lutealPhaseDays)
            val isOvulationDay = date == ovulationDateFor(date, periods, lutealPhaseDays)
            GridDayInfo(date, phase, date.isAfter(today), isOvulationDay)
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
                    // Deliberately look at the true previous/next day in the flat list, not just
                    // within this row -- a phase run that wraps from Sunday to the next Monday is
                    // still one continuous run, so its edge at the row break should stay square
                    // (roundStart/roundEnd = false there), same as any other internal join. Only
                    // the row's own horizontal gap (below) is limited to within-row neighbors,
                    // since that's a rendering concern, not a phase-continuity one.
                    val prevInfo = if (idx > 0) gridDays[idx - 1] else null
                    val nextInfo = if (idx < gridDays.lastIndex) gridDays[idx + 1] else null

                    // "Dashed" style never merges across days -- each day is its own small
                    // independent pill, closer to the older, lighter-weight look than the
                    // solid-fill style's continuous same-phase capsule.
                    val mergesWithPrev = phaseFillStyle == PhaseFillStyle.FILLED && prevInfo != null &&
                        info.phase != null && info.phase == prevInfo.phase
                    val mergesWithNext = phaseFillStyle == PhaseFillStyle.FILLED && nextInfo != null &&
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
                        isOvulationDay = info.isOvulationDay,
                        roundStart = !mergesWithPrev,
                        roundEnd = !mergesWithNext,
                        phaseFillStyle = phaseFillStyle,
                        accentColor = accentColor,
                        hasOrgasm = dateKey in orgasmDates,
                        dayEvents = eventsByDate[dateKey].orEmpty(),
                        intimacyMarker = marker,
                        hasMasturbation = dateKey in masturbationDates,
                        onClick = { onDayClick(info.date) },
                        onLongClick = { onDayLongClick(info.date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** A dashed stadium (fully-rounded-pill) outline, no fill -- the "Пунктир" phase display style,
 *  in place of the "Заливка" style's solid [Modifier.background]. Dashed-style cells are never
 *  merged into a run (see MonthGrid), so the cell is always a full stadium shape -- a plain
 *  [androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRect] with a half-height corner
 *  radius covers it without needing to trace an arbitrary [androidx.compose.ui.graphics.Shape]'s
 *  outline. */
private fun Modifier.dashedOutline(
    color: Color,
    strokeWidth: Dp = 1.6.dp,
    dash: Dp = 3.dp,
    gap: Dp = 2.5.dp
): Modifier = this.drawBehind {
    val strokeWidthPx = strokeWidth.toPx()
    // Inset by half the stroke width so the dashed line sits fully inside the cell's own
    // bounds, matching how Modifier.border draws (centered on the edge would otherwise clip).
    val inset = strokeWidthPx / 2f
    val rectSize = androidx.compose.ui.geometry.Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = rectSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rectSize.minDimension / 2f),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()), 0f)
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    phase: CyclePhase?,
    isFuture: Boolean,
    isOvulationDay: Boolean,
    roundStart: Boolean,
    roundEnd: Boolean,
    phaseFillStyle: PhaseFillStyle,
    accentColor: Color,
    hasOrgasm: Boolean,
    dayEvents: List<CalendarEvent>,
    intimacyMarker: IntimacyMarker,
    hasMasturbation: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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

    // A flat, single color per phase -- only changes at the phase boundary, never within a run.
    val phaseColor = phase?.let { appColors.phaseColor(it) }
    val isDashed = phaseFillStyle == PhaseFillStyle.DASHED
    // Every day with a known phase fills solid -- upcoming (predicted) days at full strength,
    // since what's coming up is the whole point of a forecast calendar, while already-elapsed
    // days fade back a touch to keep the emphasis forward-looking.
    val monthAlpha = if (inCurrentMonth) 1f else 0.4f
    val contentAlpha = monthAlpha * (if (isFuture) 1f else 0.6f)

    val textColor = when {
        phaseColor != null && !isDashed -> Color.White
        else -> appColors.textPrimary
    }

    // The predicted ovulation day gets a lightened fill and a soft glow behind the cell instead
    // of a badge icon -- a day that just looks subtly brighter than its ovulatory-phase
    // neighbors, rather than one more small icon to parse.
    val fillColor = if (isOvulationDay && phaseColor != null) {
        lerp(phaseColor, Color.White, 0.3f)
    } else {
        phaseColor
    }

    val cellModifier = when {
        fillColor != null && !isDashed -> Modifier
            .clip(runShape)
            .background(fillColor.copy(alpha = contentAlpha))
        fillColor != null -> Modifier.dashedOutline(fillColor.copy(alpha = contentAlpha))
        else -> Modifier
            .clip(pillShape)
            .background(appColors.warmSurface.copy(alpha = monthAlpha))
    }

    val glowModifier = if (isOvulationDay && phaseColor != null) {
        Modifier.drawBehind {
            val glowRadius = size.height * 1.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(appColors.ovulatory.copy(alpha = 0.55f), appColors.ovulatory.copy(alpha = 0f)),
                    radius = glowRadius
                ),
                radius = glowRadius
            )
        }
    } else {
        Modifier
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "dayCellScale")

    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .height(34.dp)
            .scale(pressScale)
            .then(glowModifier)
            .then(cellModifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            // Springs in from slightly undersized rather than just appearing: a low-damping
            // spring naturally overshoots past 1f before settling, reading as a soft "pop"
            // each time selection moves to this cell -- pairs with the existing tap haptic.
            val ringScale = remember { Animatable(0.7f) }
            LaunchedEffect(Unit) {
                ringScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(ringScale.value)
                    .border(
                        width = 2.dp,
                        color = if (phaseColor != null && !isDashed) Color.White else accentColor,
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
                // A white halo sits behind the colored marker ring so it stays legible even
                // when the marker color is close in hue to the phase fill under it (e.g. a
                // pink intimacy ring on a red menstrual day) -- most noticeable in light theme.
                if (markerColor != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(2.2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                    )
                }
                // The marker ring always renders at full brightness, regardless of the cell's
                // own past/future/adjacent-month fade -- it flags an actual logged entry, not
                // a prediction, so it should never read as dimmed.
                when {
                    isToday -> DottedRing(color = markerColor ?: textColor, size = 25.dp)
                    markerColor != null -> Box(
                        modifier = Modifier
                            .size(25.dp)
                            .border(1.8.dp, markerColor, CircleShape)
                    )
                }
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = textColor.copy(alpha = contentAlpha)
                )
                // A small gold star badge flags a day with a logged orgasm (sex or solo) --
                // a fixed color independent of the phase/marker palette so it always pops, on a
                // tiny white backing circle for contrast against any fill underneath.
                if (hasOrgasm) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Оргазм",
                            tint = appColors.orgasmStar,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            if (dayEvents.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    dayEvents.take(3).forEach { evt ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(evt.color))
                        )
                    }
                }
            }
        }
    }
}
