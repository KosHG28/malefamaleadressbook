package com.koshg.interlude.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koshg.interlude.data.IntimacyRepository
import com.koshg.interlude.data.MasturbationEntry
import com.koshg.interlude.data.ProposalEntry
import com.koshg.interlude.data.SexEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class IntimacyUiState(
    val sexEntries: List<SexEntry> = emptyList(),
    val proposalEntries: List<ProposalEntry> = emptyList(),
    val masturbationEntries: List<MasturbationEntry> = emptyList()
)

class IntimacyViewModel(private val repository: IntimacyRepository) : ViewModel() {

    val uiState: StateFlow<IntimacyUiState> = combine(
        repository.sexEntries, repository.proposalEntries, repository.masturbationEntries
    ) { sex, proposals, masturbation -> IntimacyUiState(sex, proposals, masturbation) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = IntimacyUiState()
        )

    fun saveSexEntry(entry: SexEntry) {
        viewModelScope.launch { repository.saveSex(entry) }
    }

    fun deleteSexEntry(entry: SexEntry) {
        viewModelScope.launch { repository.deleteSex(entry) }
    }

    fun saveProposalEntry(entry: ProposalEntry) {
        viewModelScope.launch { repository.saveProposal(entry) }
    }

    fun deleteProposalEntry(entry: ProposalEntry) {
        viewModelScope.launch { repository.deleteProposal(entry) }
    }

    fun saveMasturbationEntry(entry: MasturbationEntry) {
        viewModelScope.launch { repository.saveMasturbation(entry) }
    }

    fun deleteMasturbationEntry(entry: MasturbationEntry) {
        viewModelScope.launch { repository.deleteMasturbation(entry) }
    }

    companion object {
        fun factory(repository: IntimacyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = IntimacyViewModel(repository) as T
            }
    }
}
