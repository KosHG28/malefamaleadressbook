package com.koshg.calendar.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.calendar.data.Initiator
import com.koshg.calendar.data.IntimacyRepository
import com.koshg.calendar.data.ProposalEntry
import com.koshg.calendar.data.SexEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Immutable
data class IntimacyUiState(
    val sexEntries: List<SexEntry> = emptyList(),
    val proposalEntries: List<ProposalEntry> = emptyList()
)

class IntimacyViewModel(private val repository: IntimacyRepository) : ViewModel() {

    val uiState: StateFlow<IntimacyUiState> = combine(
        repository.sexEntries, repository.proposalEntries
    ) { sex, proposals -> IntimacyUiState(sex, proposals) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IntimacyUiState()
        )

    fun addSexEntry(date: LocalDate, initiator: Initiator, notes: String) {
        viewModelScope.launch {
            repository.saveSex(
                SexEntry(
                    id = UUID.randomUUID().toString(),
                    date = date.toString(),
                    initiator = initiator.storageValue,
                    notes = notes.trim()
                )
            )
        }
    }

    fun deleteSexEntry(entry: SexEntry) {
        viewModelScope.launch { repository.deleteSex(entry) }
    }

    fun addProposalEntry(date: LocalDate, initiator: Initiator, accepted: Boolean, notes: String) {
        viewModelScope.launch {
            repository.saveProposal(
                ProposalEntry(
                    id = UUID.randomUUID().toString(),
                    date = date.toString(),
                    initiator = initiator.storageValue,
                    accepted = accepted,
                    notes = notes.trim()
                )
            )
        }
    }

    fun deleteProposalEntry(entry: ProposalEntry) {
        viewModelScope.launch { repository.deleteProposal(entry) }
    }

    companion object {
        fun factory(repository: IntimacyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = IntimacyViewModel(repository) as T
            }
    }
}
