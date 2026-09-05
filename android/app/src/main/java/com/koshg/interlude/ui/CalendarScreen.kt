package com.koshg.interlude.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koshg.interlude.R
import com.koshg.interlude.data.CalendarEvent
import com.koshg.interlude.data.DataSnapshot
import com.koshg.interlude.data.MasturbationEntry
import com.koshg.interlude.data.PeriodEntry
import com.koshg.interlude.data.ProposalEntry
import com.koshg.interlude.data.SexEntry
import com.koshg.interlude.data.parseDataSnapshot
import com.koshg.interlude.data.toExportJson
import com.koshg.interlude.haptics.HapticEvent
import com.koshg.interlude.haptics.Haptics
import com.koshg.interlude.haptics.LocalHaptics
import com.koshg.interlude.settings.PhaseFillStyle
import com.koshg.interlude.ui.theme.LocalAdaptivePhase
import com.koshg.interlude.ui.theme.LocalMarkerColors
import com.koshg.interlude.ui.theme.LocalPalette
import com.koshg.interlude.ui.theme.LocalThemeMode
import com.koshg.interlude.ui.theme.MarkerKind
import com.koshg.interlude.ui.theme.appColors
import com.koshg.interlude.ui.theme.colorFor
import com.koshg.interlude.ui.theme.phaseColor
import com.koshg.interlude.ui.theme.resolveDark
import com.koshg.interlude.util.CyclePhase
import com.koshg.interlude.util.CycleStats
import com.koshg.interlude.util.ProactiveSuggestion
import com.koshg.interlude.util.computeProactiveSuggestion
import com.koshg.interlude.util.cycleModelOf
import com.koshg.interlude.util.cyclePhaseFor
import com.koshg.interlude.util.cyclePhaseProgressFor
import com.koshg.interlude.util.monthYearLabel
import com.koshg.interlude.util.ovulationDateFor
import com.koshg.interlude.util.ovulationFor
import com.koshg.interlude.util.phaseFor
import com.koshg.interlude.util.phaseTipForMen
import com.koshg.interlude.util.toLocalDateOrNull
import com.koshg.interlude.util.weekdayShortNames
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the "+" FAB is currently editing/creating. Carries the pre-filled date for new entries. */
sealed interface ActiveSheet {
    data class Event(val event: CalendarEvent?, val date: LocalDate) : ActiveSheet
    data class Period(val entry: PeriodEntry?, val date: LocalDate) : ActiveSheet
    data class Sex(val entry: SexEntry?, val date: LocalDate) : ActiveSheet
    data class Proposal(val entry: ProposalEntry?, val date: LocalDate) : ActiveSheet
    data class Masturbation(val entry: MasturbationEntry?, val date: LocalDate) : ActiveSheet

    /** The FAB's "add" flow — a single sheet with a type-chip row instead of a two-step chooser. */
    data class New(val date: LocalDate) : ActiveSheet

    /** A start/end pair dragged out on the month grid (see MonthGrid's range-selection mode) --
     *  opens the Period sheet pre-filled with both dates, still unsaved until the user confirms. */
    data class PeriodRangeDraft(val start: LocalDate, val end: LocalDate) : ActiveSheet
}

/**
 * Which single marker ring a day gets when it could have several. Sex outranks a proposal -- it
 * says what happened, not what was asked -- and a proposal outranks solo. `null` means no ring.
 *
 * One function rather than one rule per screen: the month grid and the year mosaic paint the same
 * days, so a day reading as intimacy in one and as solo in the other would just be a bug waiting to
 * be reported. The day's full contents are always there by tapping it (DayAgendaPanel).
 */
internal fun markerKindFor(
    dateKey: String,
    sexDates: Set<String>,
    proposalByDate: Map<String, ProposalEntry>,
    masturbationDates: Set<String>
): MarkerKind? = when {
    dateKey in sexDates -> MarkerKind.SEX
    proposalByDate[dateKey]?.answered == false -> MarkerKind.PROPOSAL_PENDING
    proposalByDate[dateKey]?.accepted == true -> MarkerKind.PROPOSAL_ACCEPTED
    proposalByDate.containsKey(dateKey) -> MarkerKind.PROPOSAL_DECLINED
    dateKey in masturbationDates -> MarkerKind.SOLO
    else -> null
}

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
    // Settings repaint the whole app. "Adaptive theme" rides along the same way: the phase is
    // resolved once here and appColors() blends accent and background from it, so every screen
    // shifts with the cycle rather than only the calendar.
    val adaptivePhase = if (cycleState.adaptiveTheme) {
        cyclePhaseProgressFor(
            LocalDate.now(),
            cycleState.periods,
            cycleState.stats.appliedMarginDays,
            cycleState.lutealPhaseDays
        )
    } else {
        null
    }

    // Marker presets are theme-independent (the user's pick); resolving them to concrete colors
    // here means nothing downstream has to know a preset carries a light/dark pair.
    val markerColors = cycleState.markerPresets.resolve(
        cycleState.themeMode.resolveDark(isSystemInDarkTheme())
    )

    CompositionLocalProvider(
        LocalPalette provides cycleState.palette,
        LocalThemeMode provides cycleState.themeMode,
        LocalAdaptivePhase provides adaptivePhase,
        LocalMarkerColors provides markerColors
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

    // Manual export/import (Settings' Data section) -- a plain JSON file the user picks a
    // destination/source for via the system document picker, independent of Android's own Auto
    // Backup: it's on-demand, portable to another device without the same Google account, and
    // readable/editable outside the app.
    //
    // The actual reading/writing and JSON work run on Dispatchers.IO, not on the picker's
    // main-thread callback: a document-provider URI can be backed by anything, cloud storage
    // included, so a slow stream would otherwise block the UI thread outright. Either way the
    // outcome is reported -- a silently swallowed failure is indistinguishable from the picker
    // simply not having done anything.
    val ioScope = rememberCoroutineScope()
    val exportDataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = DataSnapshot(
            periods = cycleState.periods,
            events = uiState.events,
            sexEntries = intimacyState.sexEntries,
            proposalEntries = intimacyState.proposalEntries,
            masturbationEntries = intimacyState.masturbationEntries
        )
        ioScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = snapshot.toExportJson()
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error(context.getString(R.string.data_open_write_failed))
                    stream.use { it.write(json.toByteArray()) }
                }
            }
            val message = context.getString(if (result.isSuccess) R.string.data_saved else R.string.data_save_failed)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val importDataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ioScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error(context.getString(R.string.data_open_read_failed))
                    parseDataSnapshot(json)
                }
            }
            result.onSuccess { snapshot ->
                snapshot.periods.forEach(cycleViewModel::savePeriod)
                snapshot.events.forEach(viewModel::saveEvent)
                snapshot.sexEntries.forEach(intimacyViewModel::saveSexEntry)
                snapshot.proposalEntries.forEach(intimacyViewModel::saveProposalEntry)
                snapshot.masturbationEntries.forEach(intimacyViewModel::saveMasturbationEntry)
                Toast.makeText(context, R.string.data_loaded, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, R.string.data_read_failed, Toast.LENGTH_SHORT).show()
            }
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
    var onboardingDismissed by remember { mutableStateOf(false) }
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
        intimacyState.sexEntries
            .filter { it.myOrgasmCount + it.partnerOrgasmCount > 0 }
            .map { it.date }
            .toSet()
    }

    // The FAB guesses intent from the selected day rather than always showing a plain "+":
    // an unanswered proposal on that day is the most actionable thing to resolve, so it takes
    // priority over the droplet, which flags a day with no period entry yet whose start is the
    // model's own next-period prediction -- the next most likely thing being logged there.
    val selectedDateKey = uiState.selectedDate.toString()
    val hasPendingProposal = proposalByDate[selectedDateKey]?.answered == false
    val isPredictedPeriodStartDay = !periodByDate.containsKey(selectedDateKey) &&
        cycleState.stats.predictedNextPeriod == uiState.selectedDate
    val fabIcon = when {
        hasPendingProposal -> Icons.Default.QuestionMark
        isPredictedPeriodStartDay -> Icons.Default.WaterDrop
        else -> Icons.Default.Add
    }
    val fabContentDescription = when {
        hasPendingProposal -> stringResource(R.string.calendar_answer_proposal)
        isPredictedPeriodStartDay -> stringResource(R.string.calendar_add_period_predicted)
        else -> stringResource(R.string.action_add)
    }

    // Already carries the cycle-phase blend when "adaptive theme" is on -- appColors() applies it
    // for the whole app (see LocalAdaptivePhase), so there's nothing phase-specific to do here.
    val dynamicAccent = appColors.accent
    val gradient = Brush.verticalGradient(listOf(appColors.gradientTop, appColors.gradientBottom))

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                val fabInteractionSource = remember { MutableInteractionSource() }
                val fabPressed by fabInteractionSource.collectIsPressedAsState()
                val fabScale by animateFloatAsState(if (fabPressed) 0.9f else 1f, label = "fabScale")
                // A plain icon FAB reads as "add" only to someone who's already used a Material
                // app enough to know the convention -- the very first sessions instead show it
                // as an ExtendedFloatingActionButton with a text label, which collapses to the
                // icon-only form once that's had a chance to sink in (see
                // CycleViewModel.showExtendedFabLabel).
                ExtendedFloatingActionButton(
                    onClick = {
                        haptics.perform(HapticEvent.Select)
                        activeSheet = if (hasPendingProposal) {
                            ActiveSheet.Proposal(proposalByDate[selectedDateKey], uiState.selectedDate)
                        } else {
                            ActiveSheet.New(uiState.selectedDate)
                        }
                    },
                    expanded = cycleViewModel.showExtendedFabLabel && !hasPendingProposal,
                    icon = {
                        AnimatedContent(targetState = fabIcon, label = "fabIcon") { icon ->
                            Icon(icon, contentDescription = fabContentDescription)
                        }
                    },
                    text = { Text(stringResource(R.string.action_add)) },
                    containerColor = dynamicAccent,
                    contentColor = Color.White,
                    interactionSource = fabInteractionSource,
                    modifier = Modifier
                        .scale(fabScale)
                        .onGloballyPositioned { fabOrigin = it.boundsInRoot().center }
                )
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

                    if (cycleViewModel.showOnboardingHint && !onboardingDismissed) {
                        OnboardingHint(
                            onDismiss = {
                                onboardingDismissed = true
                                cycleViewModel.markOnboardingSeen()
                            }
                        )
                    }

                    if (cycleState.periods.isEmpty()) {
                        // Nothing logged yet -- an all-grey grid explains nothing on its own, so
                        // this replaces the grid+agenda area entirely with one explanation and
                        // one button, rather than making a first-time user guess what the FAB is
                        // for from an empty calendar.
                        EmptyCalendarState(
                            modifier = Modifier.weight(1f),
                            onAddFirstPeriod = {
                                haptics.perform(HapticEvent.Select)
                                activeSheet = ActiveSheet.Period(null, uiState.selectedDate)
                            }
                        )
                    } else if (widthClass == WindowWidthClass.COMPACT) {
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
                            onNewEntry = { activeSheet = ActiveSheet.New(it) },
                            onRangeSelected = { start, end -> activeSheet = ActiveSheet.PeriodRangeDraft(start, end) }
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
                                onRangeSelected = { start, end -> activeSheet = ActiveSheet.PeriodRangeDraft(start, end) },
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
            marginDays = cycleState.stats.appliedMarginDays,
            lutealPhaseDays = cycleState.lutealPhaseDays,
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
            sexDates = sexByDate.keys,
            proposalByDate = proposalByDate,
            masturbationDates = masturbationDates,
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
            markerPresets = cycleState.markerPresets,
            onMarkerPresetChange = cycleViewModel::setMarkerPreset,
            onResetMarkerPresets = cycleViewModel::resetMarkerPresets,
            legendVisibility = cycleState.legendVisibility,
            onShowPhaseLegendChange = cycleViewModel::setShowPhaseLegend,
            onShowMarkerLegendChange = cycleViewModel::setShowMarkerLegend,
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
            onSave = { entry ->
                haptics.perform(HapticEvent.LogEntry)
                // Only for a genuinely new period, not an edit of an old one -- correcting a
                // typo in a months-old entry isn't "you just started your period", so it
                // shouldn't be met with a verdict on the forecast.
                if (sheet.entry == null) {
                    predictionAccuracyMessage(
                    context,
                    cycleState.stats.predictedNextPeriod,
                    entry.startDate.toLocalDateOrNull()
                )
                        ?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                }
                cycleViewModel.savePeriod(entry)
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

        is ActiveSheet.PeriodRangeDraft -> PeriodSheet(
            initialDate = sheet.start,
            initialEndDate = sheet.end,
            entry = null,
            onDismiss = { activeSheet = null },
            onSave = { entry ->
                haptics.perform(HapticEvent.LogEntry)
                predictionAccuracyMessage(
                    context,
                    cycleState.stats.predictedNextPeriod,
                    entry.startDate.toLocalDateOrNull()
                )
                    ?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                cycleViewModel.savePeriod(entry)
                activeSheet = null
            },
            onDelete = null
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
            onSavePeriod = { entry ->
                haptics.perform(HapticEvent.LogEntry)
                predictionAccuracyMessage(
                    context,
                    cycleState.stats.predictedNextPeriod,
                    entry.startDate.toLocalDateOrNull()
                )
                    ?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                cycleViewModel.savePeriod(entry)
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
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val context = LocalContext.current
        var rangeSelectionMode by remember { mutableStateOf(false) }
        val baseMonth = remember { YearMonth.now() }
        val pagerPageCount = 2401 // ~100 years either side of baseMonth — plenty of headroom
        val pagerCenterPage = pagerPageCount / 2
        val pagerState = rememberPagerState(
            initialPage = pagerCenterPage + ChronoUnit.MONTHS.between(baseMonth, uiState.viewMonth).toInt()
        ) { pagerPageCount }

        LaunchedEffect(pagerState.currentPage) {
            val swipedToMonth = baseMonth.plusMonths((pagerState.currentPage - pagerCenterPage).toLong())
            // This only differs from the current view month on a genuine user swipe -- a month
            // set from elsewhere (the header's "today", the year overview) already lands here
            // with swipedToMonth matching, since the other LaunchedEffect below just animates
            // the pager to catch up, so a haptic here never doubles up with that action's own.
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
                    monthLabel = context.monthYearLabel(month.atDay(1)),
                    rangeSelectionMode = rangeSelectionMode,
                    onToggleRangeSelectionMode = {
                        haptics.perform(HapticEvent.Toggle)
                        rangeSelectionMode = !rangeSelectionMode
                    }
                )
                if (rangeSelectionMode) {
                    Text(
                        stringResource(R.string.calendar_drag_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors().textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
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
                    rangeSelectionMode = rangeSelectionMode,
                    onDayClick = { date ->
                        haptics.perform(HapticEvent.Tap)
                        viewModel.selectDate(date)
                    },
                    onDayLongClick = { date ->
                        haptics.perform(HapticEvent.Select)
                        viewModel.selectDate(date)
                        onNewEntry(date)
                    },
                    onRangeSelected = { start, end ->
                        haptics.perform(HapticEvent.Select)
                        rangeSelectionMode = false
                        onRangeSelected(start, end)
                    }
                )
            }
        }

        // Both legends are switchable in Settings -- on by default, since nothing else on a first
        // run explains what the colors mean, and off in one tap once they're committed to memory.
        if (cycleState.legendVisibility.phases) {
            PhaseLegend()
        }
        if (cycleState.legendVisibility.markers) {
            MarkerLegend()
        }

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

/** A short verdict on the model's own forecast, shown right when a new period gets logged --
 *  comparing what the app predicted (captured *before* this save) against what actually
 *  happened builds trust in the predictions the rest of the app leans on. Null with no prior
 *  prediction to grade (no cycle history yet) or an unparseable date. */
private fun predictionAccuracyMessage(
    context: Context,
    predicted: LocalDate?,
    actualStart: LocalDate?
): String? {
    if (predicted == null || actualStart == null) return null
    val diffDays = ChronoUnit.DAYS.between(predicted, actualStart)
    return when {
        diffDays == 0L -> context.getString(R.string.prediction_exact)
        diffDays > 0 -> context.resources.getQuantityString(R.plurals.prediction_late, diffDays.toInt(), diffDays.toInt())
        else -> context.resources.getQuantityString(R.plurals.prediction_early, (-diffDays).toInt(), (-diffDays).toInt())
    }
}

/** Shown instead of the grid+agenda area until the very first period is logged -- a blank grid
 *  of grey pills explains nothing on its own, so this names the one action that unlocks the rest
 *  of the app (every phase color, prediction, and marker on this screen derives from period
 *  dates) instead of leaving a first-time user to discover the FAB unprompted. */
@Composable
private fun EmptyCalendarState(onAddFirstPeriod: () -> Unit, modifier: Modifier = Modifier) {
    val appColors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WaterDrop,
            contentDescription = null,
            tint = appColors.accent,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddFirstPeriod,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = Color.White)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.empty_action))
        }
    }
}

/** A one-time coach mark explaining the header's History/Settings icons -- shown at most once,
 *  ever (see [CycleViewModel.showOnboardingHint]). Plain declarative flow right below the header,
 *  the same pattern as [SuggestionBanner]/[EmptyCalendarState] use, rather than a custom overlay
 *  chasing the icons' exact on-screen position: a small upward-pointing triangle is enough to
 *  read as "this is about the row above" without needing to track it pixel-for-pixel. */
@Composable
private fun OnboardingHint(onDismiss: () -> Unit) {
    val appColors = appColors()
    val haptics = LocalHaptics.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.size(16.dp, 8.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color = appColors.warmSurface)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(appColors.warmSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                stringResource(R.string.onboarding_hint),
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { haptics.perform(HapticEvent.Tap); onDismiss() }) {
                Text(stringResource(R.string.onboarding_got_it), color = appColors.accent)
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
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.history_title), tint = appColors.textSecondary)
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
                Icon(Icons.Default.Today, contentDescription = stringResource(R.string.calendar_today), tint = appColors.textSecondary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.calendar_settings), tint = appColors.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stats.currentCycleDay?.let { stringResource(R.string.calendar_cycle_day, it) } ?: stringResource(R.string.calendar_cycle),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )
            if (todayPhase != null) {
                Text(
                    text = "  " + stringResource(todayPhase.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = appColors.phaseColor(todayPhase),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val subtitle = when {
            stats.predictedNextPeriodEarliest == null || stats.predictedNextPeriodLatest == null ->
                stringResource(R.string.calendar_add_period_for_forecast)
            else -> {
                val earliestDays = ChronoUnit.DAYS.between(today, stats.predictedNextPeriodEarliest)
                val latestDays = ChronoUnit.DAYS.between(today, stats.predictedNextPeriodLatest)
                when {
                    latestDays < 0 -> stringResource(R.string.calendar_period_due)
                    earliestDays <= 0 -> stringResource(R.string.calendar_period_expected_today)
                    earliestDays == latestDays -> stringResource(R.string.calendar_next_period_in, earliestDays)
                    else -> stringResource(R.string.calendar_next_period_between, earliestDays, latestDays)
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
                text = stringResource(R.string.calendar_irregular),
                style = MaterialTheme.typography.bodySmall,
                color = appColors.warning
            )
        }
    }
}

/** Just the month label and the range-selection toggle -- no prev/next chevrons: the grid is a
 *  [HorizontalPager], so swiping already moves between months, and the header's "today" button
 *  and the year overview cover longer jumps. */
@Composable
private fun MonthNav(
    monthLabel: String,
    rangeSelectionMode: Boolean,
    onToggleRangeSelectionMode: () -> Unit
) {
    val appColors = appColors()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = monthLabel.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = appColors.textPrimary
        )
        Spacer(Modifier.weight(1f))
        // Off by default so it never competes with the plain day tap/long-press this whole grid
        // otherwise relies on -- while on, dragging a finger across days in MonthGrid selects a
        // period range instead of selecting a single day.
        IconButton(onClick = onToggleRangeSelectionMode, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = if (rangeSelectionMode) {
                    stringResource(R.string.calendar_range_off)
                } else {
                    stringResource(R.string.calendar_range_on)
                },
                tint = if (rangeSelectionMode) appColors.accent else appColors.textSecondary
            )
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
        weekdayShortNames().forEach { day ->
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

private data class LegendEntry(@StringRes val labelRes: Int, val color: Color, val tooltip: String)

/** A 2-column grid of color dots + labels, each tappable to reveal a short explanation below it
 *  -- shared by [PhaseLegend] and [MarkerLegend] so both stay visually and behaviorally
 *  consistent, and a new legend elsewhere in the app doesn't mean re-deriving this layout again.
 *  Collapsed by default, only one entry open at a time. */
@Composable
private fun ExpandableLegend(entries: List<LegendEntry>, modifier: Modifier = Modifier) {
    val appColors = appColors()
    val haptics = LocalHaptics.current
    var expandedIndex by remember(entries) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row-major 2-column grid, each cell equal-width, so dots and labels line up on a
        // consistent grid regardless of how long each entry's label is.
        entries.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { colIndex, entry ->
                    val index = rowIndex * 2 + colIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptics.perform(HapticEvent.Tap)
                                expandedIndex = if (expandedIndex == index) null else index
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(entry.color)
                        )
                        Text(
                            text = stringResource(entry.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors.textSecondary
                        )
                    }
                }
                // A row-level filler for the odd last entry, so its cell keeps half the width
                // rather than stretching. Outside the cell loop, which therefore emits exactly
                // one shape per iteration -- see MonthMosaic for what a loop that doesn't can do.
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        AnimatedVisibility(
            visible = expandedIndex != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            expandedIndex?.let { index ->
                Text(
                    text = entries[index].tooltip,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PhaseLegend() {
    val appColors = appColors()
    val context = LocalContext.current
    val entries = remember(appColors, context) {
        CyclePhase.entries.map { phase ->
            LegendEntry(phase.labelRes, appColors.phaseColor(phase), context.phaseTipForMen(phase))
        }
    }
    ExpandableLegend(entries, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

/** What each colored ring/badge on a day cell means -- the ring/badge itself (DayCell) only has
 *  room for color and weight, not a label, so a first-time (especially male) reader has nowhere
 *  else to learn that pink means sex and green means solo. */
@Composable
private fun MarkerLegend() {
    val markerColors = LocalMarkerColors.current
    // Built straight rather than inside remember: stringResource is itself a composable read, so
    // it cannot run in remember's lambda, and the list is five entries -- not worth caching.
    val entries =
        listOf(
            LegendEntry(
                MarkerKind.SEX.labelRes,
                markerColors.sex,
                stringResource(R.string.legend_sex_tip)
            ),
            LegendEntry(
                MarkerKind.PROPOSAL_ACCEPTED.labelRes,
                markerColors.proposalAccepted,
                stringResource(R.string.legend_accepted_tip)
            ),
            LegendEntry(
                MarkerKind.PROPOSAL_DECLINED.labelRes,
                markerColors.proposalDeclined,
                stringResource(R.string.legend_declined_tip)
            ),
            LegendEntry(
                MarkerKind.PROPOSAL_PENDING.labelRes,
                markerColors.proposalPending,
                stringResource(R.string.legend_pending_tip)
            ),
            LegendEntry(
                MarkerKind.SOLO.labelRes,
                markerColors.solo,
                stringResource(R.string.legend_solo_tip)
            )
        )
    ExpandableLegend(entries, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
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
                stringResource(R.string.suggestion_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary
            )
            Text(
                stringResource(suggestion.reason.textRes, stringResource(suggestion.idea.textRes)),
                style = MaterialTheme.typography.bodySmall,
                color = appColors.textSecondary
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.calendar_hide_hint),
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
    rangeSelectionMode: Boolean,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit,
    onRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstOfMonth = viewMonth.atDay(1)
    val firstWeekdayIndex = firstOfMonth.dayOfWeek.value - 1 // Monday = 0 .. Sunday = 6
    val gridStart = firstOfMonth.minusDays(firstWeekdayIndex.toLong())
    // Always 6 weeks, even though a month only strictly needs 5 sometimes -- a fixed row
    // count keeps every page the same height, so swiping between months doesn't visibly
    // resize the grid.
    val weeks = 6

    // One model per page rather than one per day: the grid asks 44 times, and each raw-list call
    // re-parses and re-sorts the whole period history before it can answer.
    val cycleModel = remember(periods) { cycleModelOf(periods) }
    val gridDays = remember(viewMonth, cycleModel, marginDays, lutealPhaseDays) {
        (0 until weeks * 7).map { i ->
            val date = gridStart.plusDays(i.toLong())
            val phase = cycleModel.phaseFor(date, marginDays, lutealPhaseDays)
            val isOvulationDay = date == cycleModel.ovulationFor(date, lutealPhaseDays)
            GridDayInfo(date, phase, date.isAfter(today), isOvulationDay)
        }
    }

    // The phase immediately outside either end of this page's own grid -- so the very first/last
    // cell can tell whether it merges into a run that actually started/continues on the adjacent
    // month's page, instead of always rounding off there just because this page's own gridDays
    // list has nothing before/after it to compare against.
    val phaseBeforeGrid = remember(gridStart, cycleModel, marginDays, lutealPhaseDays) {
        cycleModel.phaseFor(gridStart.minusDays(1), marginDays, lutealPhaseDays)
    }
    val phaseAfterGrid = remember(gridStart, cycleModel, marginDays, lutealPhaseDays) {
        cycleModel.phaseFor(gridStart.plusDays((weeks * 7).toLong()), marginDays, lutealPhaseDays)
    }

    // Hit-testing for the range-selection drag below: each cell reports its own on-screen
    // bounds in window coordinates (so they're directly comparable across rows/weeks, unlike
    // parent-relative bounds which differ per week's own Row), and the drag gesture just checks
    // which cell's bounds contain the pointer, converted to the same coordinate space via this
    // Column's own captured LayoutCoordinates.
    val cellBounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragAnchor by remember { mutableStateOf<LocalDate?>(null) }
    var dragCurrent by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(rangeSelectionMode) {
        if (!rangeSelectionMode) {
            dragAnchor = null
            dragCurrent = null
        }
    }
    val dragRange = dragAnchor?.let { anchor -> dragCurrent?.let { current -> minOf(anchor, current)..maxOf(anchor, current) } }

    Column(
        modifier = Modifier
            .onGloballyPositioned { gridCoordinates = it }
            .then(
                // Attached only in range-selection mode, so the default tap/long-press path on
                // each DayCell below is completely untouched otherwise -- this drag detector
                // exists at all only when the user has explicitly opted into it (see MonthNav).
                if (rangeSelectionMode) {
                    Modifier.pointerInput(gridDays) {
                        fun hitTest(localPosition: Offset): LocalDate? {
                            val coords = gridCoordinates ?: return null
                            val windowPos = coords.localToWindow(localPosition)
                            return cellBounds.entries.firstOrNull { it.value.contains(windowPos) }?.key
                        }
                        detectDragGestures(
                            onDragStart = { offset ->
                                val hit = hitTest(offset)
                                dragAnchor = hit
                                dragCurrent = hit
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                hitTest(change.position)?.let { dragCurrent = it }
                            },
                            onDragEnd = {
                                val start = dragAnchor
                                val end = dragCurrent
                                if (start != null && end != null && start != end) {
                                    onRangeSelected(minOf(start, end), maxOf(start, end))
                                }
                                dragAnchor = null
                                dragCurrent = null
                            },
                            onDragCancel = {
                                dragAnchor = null
                                dragCurrent = null
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                for (dow in 0 until 7) {
                    val idx = week * 7 + dow
                    val info = gridDays[idx]
                    // Deliberately look at the true previous/next day, not just within this page's
                    // own grid -- a phase run that wraps from Sunday to the next Monday is still
                    // one continuous run, so its edge at the row break should stay square
                    // (roundStart/roundEnd = false there), same as any other internal join. That
                    // also applies at the very first/last cell of the grid, which is why the
                    // out-of-range ends fall back to phaseBeforeGrid/phaseAfterGrid instead of null
                    // -- otherwise a run that merely continues from the previous month's page
                    // would always cap off as if it started right here. Only the row's own
                    // horizontal gap (below) is limited to within-row neighbors, since that's a
                    // rendering concern, not a phase-continuity one.
                    val prevPhase = if (idx > 0) gridDays[idx - 1].phase else phaseBeforeGrid
                    val nextPhase = if (idx < gridDays.lastIndex) gridDays[idx + 1].phase else phaseAfterGrid

                    // "Dashed" style never merges across days -- each day is its own small
                    // independent pill, closer to the older, lighter-weight look than the
                    // solid-fill style's continuous same-phase capsule.
                    val mergesWithPrev = phaseFillStyle == PhaseFillStyle.FILLED &&
                        info.phase != null && info.phase == prevPhase
                    val mergesWithNext = phaseFillStyle == PhaseFillStyle.FILLED &&
                        info.phase != null && info.phase == nextPhase

                    if (dow > 0) {
                        Spacer(Modifier.width(if (mergesWithPrev) 0.dp else 3.dp))
                    }

                    val dateKey = info.date.toString()
                    val marker = markerKindFor(dateKey, sexByDate.keys, proposalByDate, masturbationDates)

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
                        markerKind = marker,
                        isDragHighlighted = dragRange?.let { info.date in it } == true,
                        onClick = { onDayClick(info.date) },
                        onLongClick = { onDayLongClick(info.date) },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { cellBounds[info.date] = it.boundsInWindow() }
                    )
                }
            }
        }
    }
}

/** A dashed stadium (fully-rounded-pill) outline, no fill -- the dashed phase display style,
 *  in place of the filled style's solid [Modifier.background]. Dashed-style cells are never
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

/** How many days out a predicted day's fill has drained to its floor -- roughly one forecast
 *  cycle, past which the drain holds flat instead of continuing indefinitely. */
private const val FUTURE_FADE_HORIZON_DAYS = 30f

/** How much color a day's fill gives up to read as de-emphasised, per reason. De-emphasis is
 *  desaturation at a constant lightness, never transparency: alpha toward the screen behind
 *  bleaches a capsule over a light background and dims one over a dark background, so either way
 *  the fill's composited lightness moves. A lightness-driven day-number color then flips between
 *  white and dark ink partway along a single phase run -- days of one phase, one apparent color,
 *  yet different digits -- because the fade crossed the threshold mid-run. Draining the color
 *  instead leaves every filled cell at the lightness its phase color already had, so a single ink
 *  color is correct for the entire grid and there is no threshold left to cross. */
private const val FUTURE_MAX_DRAIN = 0.35f
private const val ELAPSED_DRAIN = 0.3f
private const val ADJACENT_MONTH_DRAIN = 0.3f

/** However many reasons stack up on one day, its fill gives up at most this much of its color.
 *  The reasons compound, and at the first cut an adjacent month's far-future days collected enough
 *  of them to land almost fully grey -- which read as a different kind of day altogether rather
 *  than a quieter one. De-emphasis is meant to recede, not to strip a day of what phase it is. */
private const val MAX_TOTAL_DRAIN = 0.5f

/** An adjacent month's day number, on its by-then muted capsule. Dimmer than the current month's
 *  but not the 0.4 the fill itself used to carry -- the drained capsule says "other month" on its
 *  own, so the digit only has to agree, not disappear. */
private const val ADJACENT_MONTH_TEXT_ALPHA = 0.75f

/** How far the predicted ovulation day's fill is lifted toward white, so it reads as a touch
 *  brighter than its ovulatory-phase neighbours. Deliberately small: a lift is the one thing here
 *  that does move a cell's lightness, and at the 0.3 it used to be it pushed the day's contrast
 *  with the white number below what the rest of the grid holds. */
private const val OVULATION_DAY_LIFT = 0.18f

/** One thickness for every day-cell marker ring: the color alone says which kind it is. */
private val MARKER_RING_WIDTH = 2.2.dp

/** The grey that reads as light as this color does -- its relative luminance carried back through
 *  the sRGB transfer curve. */
private fun Color.toNeutral(): Color {
    val y = luminance()
    val channel = if (y <= 0.0031308f) y * 12.92f else 1.055f * y.pow(1f / 2.4f) - 0.055f
    val v = channel.coerceIn(0f, 1f)
    return Color(v, v, v)
}

/** Gives up [fraction] of this color's saturation while holding how light it reads, the one
 *  de-emphasis channel the day grid uses -- see the drain constants above for why not alpha. */
private fun Color.drained(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f == 0f) this else lerp(this, toNeutral(), f)
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
    markerKind: MarkerKind?,
    isDragHighlighted: Boolean,
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
    // Every day with a known phase fills solid, at full opacity whatever its emphasis. What a
    // de-emphasised day gives up is saturation, not opacity: the three reasons a day can be
    // played down each drain some of its color, and they compound on whatever color is left.
    // The one exception is a day with no phase at all, whose surface is a background tone rather
    // than a color -- there is no saturation there to drain, so it still recedes by alpha.
    val monthAlpha = if (inCurrentMonth) 1f else 0.4f

    // The further out a predicted day sits, the less certain that prediction is, and the less
    // color its fill keeps. Ramps to FUTURE_MAX_DRAIN by FUTURE_FADE_HORIZON_DAYS out (roughly
    // one full forecast cycle), then holds flat rather than draining indefinitely.
    val daysAhead = if (isFuture) ChronoUnit.DAYS.between(LocalDate.now(), date) else 0L
    val futureDrain = if (isFuture) {
        (daysAhead.toFloat() / FUTURE_FADE_HORIZON_DAYS).coerceIn(0f, 1f) * FUTURE_MAX_DRAIN
    } else {
        0f
    }
    // Elapsed days hold their color a little less firmly than upcoming ones -- what's coming up is
    // the point of a forecast calendar -- and an adjacent month's days nearly let go of it, which
    // is what marks them as outside the month being read.
    val elapsedDrain = if (isFuture) 0f else ELAPSED_DRAIN
    val monthDrain = if (inCurrentMonth) 0f else ADJACENT_MONTH_DRAIN
    val drain = (1f - (1f - futureDrain) * (1f - elapsedDrain) * (1f - monthDrain))
        .coerceAtMost(MAX_TOTAL_DRAIN)

    // The predicted ovulation day gets a lightened fill and a soft glow behind the cell instead
    // of a badge icon -- a day that just looks subtly brighter than its ovulatory-phase
    // neighbors, rather than one more small icon to parse.
    val baseFill = if (isOvulationDay && phaseColor != null) {
        lerp(phaseColor, Color.White, OVULATION_DAY_LIFT)
    } else {
        phaseColor
    }
    val fillColor = baseFill?.drained(drain)

    // No threshold, because there is nothing left for one to decide: draining color never changes
    // how light a fill reads, so every filled cell in the grid sits in the same narrow lightness
    // band its phase colors were chosen in, and white is the right ink on all of them. A cell with
    // no phase at all is a different kind of cell -- no capsule, background-toned surface -- and
    // takes the normal ink.
    val textColor = if (fillColor != null && !isDashed) Color.White else appColors.textPrimary

    val cellModifier = when {
        fillColor != null && !isDashed -> Modifier
            .clip(runShape)
            .background(fillColor)
        fillColor != null -> Modifier.dashedOutline(fillColor)
        else -> Modifier
            .clip(pillShape)
            .background(appColors.warmSurface.copy(alpha = monthAlpha))
    }

    val glowModifier = if (isOvulationDay && phaseColor != null) {
        Modifier.drawBehind {
            // Kept close to the cell: at 1.5x the cell height this spilled well past the capsule
            // and read as a smudge on the calendar, most obviously in light theme.
            val glowRadius = size.height * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(appColors.ovulatory.copy(alpha = 0.4f), appColors.ovulatory.copy(alpha = 0f)),
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
        // The live preview while dragging out a period range (see MonthGrid's range-selection
        // mode) -- a plain translucent fill, deliberately simpler than the selection ring below,
        // since it's provisional and disappears the moment the drag ends either way.
        if (isDragHighlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accentColor.copy(alpha = 0.28f), if (phaseColor != null) runShape else pillShape)
            )
        }
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
        // One colored ring around the date per day, and nothing else -- no corner badges beyond
        // the orgasm star (see [markerKindFor] for which one a day gets). Calendar-event colors
        // stay as the small dots below the cell, since those are a different, possibly
        // multi-valued kind of marker.
        val markerColors = LocalMarkerColors.current
        val markerColor = markerKind?.let { markerColors.colorFor(it) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // A thin white halo sits just behind the colored marker ring so it stays legible
                // even when the marker color is close in hue to the phase fill under it (e.g. a
                // pink intimacy ring on a red menstrual day). Deliberately thinner and only
                // slightly larger than the ring itself -- it used to be thicker than the ring it
                // was meant to support, so the white outline read as the marker and the actual
                // color nearly disappeared.
                if (markerColor != null) {
                    Box(
                        modifier = Modifier
                            .size(27.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                }
                // Every marker ring is the same thickness and full strength -- only the color
                // differs, so nothing has to be learned about what a thinner or fainter ring is
                // supposed to mean. It also always renders at full brightness regardless of the
                // cell's own past/future/adjacent-month fade: it flags an actual logged entry,
                // not a prediction, so it should never read as dimmed.
                when {
                    isToday -> DottedRing(color = markerColor ?: textColor, size = 25.dp)
                    markerColor != null -> Box(
                        modifier = Modifier
                            .size(25.dp)
                            .border(MARKER_RING_WIDTH, markerColor, CircleShape)
                    )
                }
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    // Only the adjacent-month dim applies here, not the elapsed/far-future one:
                    // the fill already carries "past" and "less certain", and fading the number
                    // on top of an already-drained capsule just made it hard to read.
                    color = textColor.copy(
                        alpha = if (inCurrentMonth) 1f else ADJACENT_MONTH_TEXT_ALPHA
                    )
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
                            contentDescription = stringResource(R.string.calendar_orgasm),
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
