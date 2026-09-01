package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.CalendarEvent
import com.koshg.calendar.data.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Immutable
data class CalendarUiState(
    val viewMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<CalendarEvent> = emptyList()
) {
    val eventsByDate: Map<String, List<CalendarEvent>>
        get() = events
            .sortedWith(compareBy({ !it.allDay }, { it.startTime ?: "" }))
            .groupBy { it.date }

    val selectedDateEvents: List<CalendarEvent>
        get() = eventsByDate[selectedDate.toString()].orEmpty()
}

class CalendarViewModel(private val repository: EventRepository) : ViewModel() {

    private val viewMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<CalendarUiState> = combine(
        viewMonth, selectedDate, repository.allEvents
    ) { month, selected, events ->
        CalendarUiState(month, selected, events)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState()
    )

    fun goToPreviousMonth() {
        viewMonth.value = viewMonth.value.minusMonths(1)
    }

    fun goToNextMonth() {
        viewMonth.value = viewMonth.value.plusMonths(1)
    }

    /** Used by the swipeable month pager to report the month the user has landed on. */
    fun setViewMonth(month: YearMonth) {
        viewMonth.value = month
    }

    fun goToToday() {
        val today = LocalDate.now()
        viewMonth.value = YearMonth.from(today)
        selectedDate.value = today
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        viewMonth.value = YearMonth.from(date)
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch { repository.save(event) }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch { repository.delete(event) }
    }

    companion object {
        fun factory(repository: EventRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CalendarViewModel(repository) as T
            }
    }
}

fun newEventId(): String = UUID.randomUUID().toString()
