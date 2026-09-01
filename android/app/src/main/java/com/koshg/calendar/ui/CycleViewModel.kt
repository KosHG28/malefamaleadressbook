package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.CycleRepository
import com.koshg.calendar.data.PeriodEntry
import com.koshg.calendar.util.CycleStats
import com.koshg.calendar.util.computeCycleStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Immutable
data class CycleUiState(
    val periods: List<PeriodEntry> = emptyList(),
    val stats: CycleStats = computeCycleStats(emptyList())
)

class CycleViewModel(private val repository: CycleRepository) : ViewModel() {

    val uiState: StateFlow<CycleUiState> = repository.periods
        .map { periods -> CycleUiState(periods = periods, stats = computeCycleStats(periods)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CycleUiState()
        )

    fun addPeriod(date: LocalDate, notes: String) {
        viewModelScope.launch {
            repository.save(
                PeriodEntry(id = UUID.randomUUID().toString(), startDate = date.toString(), notes = notes.trim())
            )
        }
    }

    fun deletePeriod(entry: PeriodEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    companion object {
        fun factory(repository: CycleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = CycleViewModel(repository) as T
            }
    }
}
