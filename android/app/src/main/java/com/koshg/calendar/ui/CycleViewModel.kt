package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.settings.CyclePreferences
import com.koshg.calendar.settings.PhaseFillStyle
import com.koshg.calendar.ui.theme.MarkerColors
import com.koshg.calendar.ui.theme.MarkerKind
import com.koshg.calendar.ui.theme.Palette
import com.koshg.calendar.ui.theme.ThemeMode
import com.koshg.calendar.util.CycleStats
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS
import com.koshg.calendar.util.computeCycleStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How many days a dismissed suggestion banner stays snoozed before it may reappear. */
private const val SUGGESTION_SNOOZE_DAYS = 7L

/** How many app opens the FAB keeps its "Добавить" text label for -- see
 *  [CycleViewModel.showExtendedFabLabel]. */
private const val FAB_LABEL_SESSION_THRESHOLD = 3

@Immutable
data class CycleUiState(
    val periods: List<PeriodEntry> = emptyList(),
    val stats: CycleStats = computeCycleStats(emptyList()),
    val lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS,
    val adaptiveTheme: Boolean = false,
    val phaseFillStyle: PhaseFillStyle = PhaseFillStyle.FILLED,
    val palette: Palette = Palette.WINE,
    val suggestionsEnabled: Boolean = true,
    val suggestionDismissedUntilEpochDay: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val remindersEnabled: Boolean = false,
    val markerColors: MarkerColors = MarkerColors(),
    val legendVisibility: LegendVisibility = LegendVisibility()
)

/** Which of the two calendar legends are switched on. One value rather than two separate flows so
 *  they cost a single slot in the [combine] chain below. */
@Immutable
data class LegendVisibility(
    val phases: Boolean = true,
    val markers: Boolean = true
)

/** The appearance/suggestion toggles bundled into one flow (below) so combining them with
 *  [repository.periods] and [lutealPhaseDays] doesn't need a >5-argument [combine] overload. */
private data class DisplayPrefs(
    val adaptiveTheme: Boolean,
    val phaseFillStyle: PhaseFillStyle,
    val palette: Palette,
    val suggestionsEnabled: Boolean,
    val suggestionDismissedUntilEpochDay: Long,
    val themeMode: ThemeMode,
    val remindersEnabled: Boolean,
    val markerColors: MarkerColors,
    val legendVisibility: LegendVisibility
)

/** The first five toggles, pre-combined so adding [ThemeMode] on top only needs a 2-argument
 *  [combine] rather than restructuring everything into a >5-argument overload. */
private data class BaseDisplayPrefs(
    val adaptiveTheme: Boolean,
    val phaseFillStyle: PhaseFillStyle,
    val palette: Palette,
    val suggestionsEnabled: Boolean,
    val suggestionDismissedUntilEpochDay: Long
)

class CycleViewModel(
    private val repository: CycleRepository,
    private val preferences: CyclePreferences
) : ViewModel() {

    /** Fixed for this ViewModel's lifetime, not part of the reactive [uiState] below -- whether
     *  the FAB should show its "Добавить" text label this app open. MainActivity records the
     *  open (via [CyclePreferences.recordAppOpen]) before this ViewModel is first touched, so
     *  the count read here already includes the current session. */
    val showExtendedFabLabel: Boolean = preferences.appOpenCount <= FAB_LABEL_SESSION_THRESHOLD

    /** Same one-shot idea as [showExtendedFabLabel]: whether the first-launch coach mark (the
     *  History/Settings icons hint, see CalendarScreen) has never been shown and dismissed. */
    val showOnboardingHint: Boolean = !preferences.onboardingSeen

    fun markOnboardingSeen() {
        preferences.onboardingSeen = true
    }

    private val lutealPhaseDays = MutableStateFlow(preferences.lutealPhaseDays)
    private val adaptiveTheme = MutableStateFlow(preferences.adaptiveTheme)
    private val phaseFillStyle = MutableStateFlow(preferences.phaseFillStyle)
    private val palette = MutableStateFlow(preferences.palette)
    private val suggestionsEnabled = MutableStateFlow(preferences.suggestionsEnabled)
    private val suggestionDismissedUntilEpochDay = MutableStateFlow(preferences.suggestionDismissedUntilEpochDay)
    private val themeMode = MutableStateFlow(preferences.themeMode)
    private val remindersEnabled = MutableStateFlow(preferences.remindersEnabled)
    private val markerColors = MutableStateFlow(preferences.markerColors)
    private val legendVisibility = MutableStateFlow(
        LegendVisibility(preferences.showPhaseLegend, preferences.showMarkerLegend)
    )

    private val baseDisplayPrefs = combine(
        adaptiveTheme, phaseFillStyle, palette, suggestionsEnabled, suggestionDismissedUntilEpochDay
    ) { adaptive, fillStyle, pal, suggestionsOn, dismissedUntil ->
        BaseDisplayPrefs(adaptive, fillStyle, pal, suggestionsOn, dismissedUntil)
    }

    private val displayPrefs = combine(
        baseDisplayPrefs, themeMode, remindersEnabled, markerColors, legendVisibility
    ) { base, mode, remindersOn, markers, legends ->
        DisplayPrefs(
            base.adaptiveTheme,
            base.phaseFillStyle,
            base.palette,
            base.suggestionsEnabled,
            base.suggestionDismissedUntilEpochDay,
            mode,
            remindersOn,
            markers,
            legends
        )
    }

    val uiState: StateFlow<CycleUiState> = combine(
        repository.periods, lutealPhaseDays, displayPrefs
    ) { periods, luteal, prefs ->
        CycleUiState(
            periods = periods,
            stats = computeCycleStats(periods, lutealPhaseDays = luteal),
            lutealPhaseDays = luteal,
            adaptiveTheme = prefs.adaptiveTheme,
            phaseFillStyle = prefs.phaseFillStyle,
            palette = prefs.palette,
            suggestionsEnabled = prefs.suggestionsEnabled,
            suggestionDismissedUntilEpochDay = prefs.suggestionDismissedUntilEpochDay,
            themeMode = prefs.themeMode,
            remindersEnabled = prefs.remindersEnabled,
            markerColors = prefs.markerColors,
            legendVisibility = prefs.legendVisibility
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CycleUiState(
            lutealPhaseDays = preferences.lutealPhaseDays,
            adaptiveTheme = preferences.adaptiveTheme,
            phaseFillStyle = preferences.phaseFillStyle,
            palette = preferences.palette,
            suggestionsEnabled = preferences.suggestionsEnabled,
            suggestionDismissedUntilEpochDay = preferences.suggestionDismissedUntilEpochDay,
            themeMode = preferences.themeMode,
            remindersEnabled = preferences.remindersEnabled,
            markerColors = preferences.markerColors,
            legendVisibility = LegendVisibility(
                preferences.showPhaseLegend,
                preferences.showMarkerLegend
            )
        )
    )

    fun savePeriod(entry: PeriodEntry) {
        viewModelScope.launch { repository.save(entry) }
    }

    fun deletePeriod(entry: PeriodEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    /** Persists the custom luteal-phase length and reflows every ovulation/fertile-window prediction from it. */
    fun setLutealPhaseDays(days: Int) {
        preferences.lutealPhaseDays = days
        lutealPhaseDays.value = days
    }

    fun setAdaptiveTheme(enabled: Boolean) {
        preferences.adaptiveTheme = enabled
        adaptiveTheme.value = enabled
    }

    fun setPhaseFillStyle(style: PhaseFillStyle) {
        preferences.phaseFillStyle = style
        phaseFillStyle.value = style
    }

    fun setPalette(newPalette: Palette) {
        preferences.palette = newPalette
        palette.value = newPalette
    }

    fun setSuggestionsEnabled(enabled: Boolean) {
        preferences.suggestionsEnabled = enabled
        suggestionsEnabled.value = enabled
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.themeMode = mode
        themeMode.value = mode
    }

    /** The caller (Settings) is expected to have already secured the POST_NOTIFICATIONS permission
     *  before passing `true` -- see [CyclePreferences.remindersEnabled] for the WorkManager sync. */
    fun setRemindersEnabled(enabled: Boolean) {
        preferences.remindersEnabled = enabled
        remindersEnabled.value = enabled
    }

    fun setMarkerColor(kind: MarkerKind, color: Color) {
        preferences.setMarkerColor(kind, color)
        markerColors.value = preferences.markerColors
    }

    /** Drops every per-marker color override back to the built-in defaults. */
    fun resetMarkerColors() {
        preferences.resetMarkerColors()
        markerColors.value = preferences.markerColors
    }

    fun setShowPhaseLegend(show: Boolean) {
        preferences.showPhaseLegend = show
        legendVisibility.value = legendVisibility.value.copy(phases = show)
    }

    fun setShowMarkerLegend(show: Boolean) {
        preferences.showMarkerLegend = show
        legendVisibility.value = legendVisibility.value.copy(markers = show)
    }

    /** Snoozes the suggestion banner for [SUGGESTION_SNOOZE_DAYS] regardless of which suggestion was showing. */
    fun dismissSuggestion() {
        val until = LocalDate.now().plusDays(SUGGESTION_SNOOZE_DAYS).toEpochDay()
        preferences.suggestionDismissedUntilEpochDay = until
        suggestionDismissedUntilEpochDay.value = until
    }

    companion object {
        fun factory(repository: CycleRepository, preferences: CyclePreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CycleViewModel(repository, preferences) as T
            }
    }
}
