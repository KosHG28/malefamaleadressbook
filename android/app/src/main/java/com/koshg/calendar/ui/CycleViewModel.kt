package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.settings.CyclePreferences
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
    val lutealPhaseDays: Int = DEFAULT_LUTEAL_PHASE_DAYS
)

class CycleViewModel(
    private val repository: CycleRepository,
    private val preferences: CyclePreferences
) : ViewModel() {

    private val lutealPhaseDays = MutableStateFlow(preferences.lutealPhaseDays)

    val uiState: StateFlow<CycleUiState> = combine(repository.periods, lutealPhaseDays) { periods, luteal ->
        CycleUiState(periods = periods, stats = computeCycleStats(periods, lutealPhaseDays = luteal), lutealPhaseDays = luteal)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CycleUiState(lutealPhaseDays = preferences.lutealPhaseDays)
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

    companion object {
        fun factory(repository: CycleRepository, preferences: CyclePreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CycleViewModel(repository, preferences) as T
            }
    }
}
