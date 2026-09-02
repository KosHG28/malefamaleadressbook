package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.settings.CyclePreferences
import com.koshg.calendar.settings.PhaseFillStyle
import com.koshg.calendar.ui.theme.Palette
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

@Immutable
data class CycleUiState(
    val periods: List<PeriodEntry> = emptyList(),
    val stats: CycleStats = computeCycleStats(emptyList()),
    val lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS,
    val adaptiveTheme: Boolean = false,
    val phaseFillStyle: PhaseFillStyle = PhaseFillStyle.FILLED,
    val palette: Palette = Palette.WINE,
    val suggestionsEnabled: Boolean = true,
    val suggestionDismissedUntilEpochDay: Long = 0L
)

/** The appearance/suggestion toggles bundled into one flow (below) so combining them with
 *  [repository.periods] and [lutealPhaseDays] doesn't need a >5-argument [combine] overload. */
private data class DisplayPrefs(
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

    private val lutealPhaseDays = MutableStateFlow(preferences.lutealPhaseDays)
    private val adaptiveTheme = MutableStateFlow(preferences.adaptiveTheme)
    private val phaseFillStyle = MutableStateFlow(preferences.phaseFillStyle)
    private val palette = MutableStateFlow(preferences.palette)
    private val suggestionsEnabled = MutableStateFlow(preferences.suggestionsEnabled)
    private val suggestionDismissedUntilEpochDay = MutableStateFlow(preferences.suggestionDismissedUntilEpochDay)

    private val displayPrefs = combine(
        adaptiveTheme, phaseFillStyle, palette, suggestionsEnabled, suggestionDismissedUntilEpochDay
    ) { adaptive, fillStyle, pal, suggestionsOn, dismissedUntil ->
        DisplayPrefs(adaptive, fillStyle, pal, suggestionsOn, dismissedUntil)
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
            suggestionDismissedUntilEpochDay = prefs.suggestionDismissedUntilEpochDay
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
            suggestionDismissedUntilEpochDay = preferences.suggestionDismissedUntilEpochDay
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
