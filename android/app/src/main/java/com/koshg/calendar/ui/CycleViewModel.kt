package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.settings.CyclePreferences
import com.koshg.calendar.settings.PhaseFillStyle
import com.koshg.calendar.util.CycleStats
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS
import com.koshg.calendar.util.computeCycleStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class CycleUiState(
    val periods: List<PeriodEntry> = emptyList(),
    val stats: CycleStats = computeCycleStats(emptyList()),
    val lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS,
    val adaptiveTheme: Boolean = false,
    val gradientDayFill: Boolean = false,
    val vividColors: Boolean = false,
    val phaseFillStyle: PhaseFillStyle = PhaseFillStyle.FILLED
)

/** The appearance toggles bundled into one flow (below) so combining them with [repository.periods]
 *  and [lutealPhaseDays] doesn't need a >5-argument [combine] overload. */
private data class DisplayPrefs(
    val adaptiveTheme: Boolean,
    val gradientDayFill: Boolean,
    val vividColors: Boolean,
    val phaseFillStyle: PhaseFillStyle
)

class CycleViewModel(
    private val repository: CycleRepository,
    private val preferences: CyclePreferences
) : ViewModel() {

    private val lutealPhaseDays = MutableStateFlow(preferences.lutealPhaseDays)
    private val adaptiveTheme = MutableStateFlow(preferences.adaptiveTheme)
    private val gradientDayFill = MutableStateFlow(preferences.gradientDayFill)
    private val vividColors = MutableStateFlow(preferences.vividColors)
    private val phaseFillStyle = MutableStateFlow(preferences.phaseFillStyle)

    private val displayPrefs = combine(
        adaptiveTheme, gradientDayFill, vividColors, phaseFillStyle
    ) { adaptive, gradient, vivid, fillStyle -> DisplayPrefs(adaptive, gradient, vivid, fillStyle) }

    val uiState: StateFlow<CycleUiState> = combine(
        repository.periods, lutealPhaseDays, displayPrefs
    ) { periods, luteal, prefs ->
        CycleUiState(
            periods = periods,
            stats = computeCycleStats(periods, lutealPhaseDays = luteal),
            lutealPhaseDays = luteal,
            adaptiveTheme = prefs.adaptiveTheme,
            gradientDayFill = prefs.gradientDayFill,
            vividColors = prefs.vividColors,
            phaseFillStyle = prefs.phaseFillStyle
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CycleUiState(
            lutealPhaseDays = preferences.lutealPhaseDays,
            adaptiveTheme = preferences.adaptiveTheme,
            gradientDayFill = preferences.gradientDayFill,
            vividColors = preferences.vividColors,
            phaseFillStyle = preferences.phaseFillStyle
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

    fun setGradientDayFill(enabled: Boolean) {
        preferences.gradientDayFill = enabled
        gradientDayFill.value = enabled
    }

    fun setVividColors(enabled: Boolean) {
        preferences.vividColors = enabled
        vividColors.value = enabled
    }

    fun setPhaseFillStyle(style: PhaseFillStyle) {
        preferences.phaseFillStyle = style
        phaseFillStyle.value = style
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
