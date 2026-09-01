package com.koshg.calendar.data

import kotlinx.coroutines.flow.Flow

class IntimacyRepository(
    private val sexDao: SexDao,
    private val proposalDao: ProposalDao,
    private val masturbationDao: MasturbationDao
) {
    val sexEntries: Flow<List<SexEntry>> = sexDao.getAll()
    val proposalEntries: Flow<List<ProposalEntry>> = proposalDao.getAll()
    val masturbationEntries: Flow<List<MasturbationEntry>> = masturbationDao.getAll()

    suspend fun saveSex(entry: SexEntry) = sexDao.upsert(entry)
    suspend fun deleteSex(entry: SexEntry) = sexDao.delete(entry)

    suspend fun saveProposal(entry: ProposalEntry) = proposalDao.upsert(entry)
    suspend fun deleteProposal(entry: ProposalEntry) = proposalDao.delete(entry)

    suspend fun saveMasturbation(entry: MasturbationEntry) = masturbationDao.upsert(entry)
    suspend fun deleteMasturbation(entry: MasturbationEntry) = masturbationDao.delete(entry)
}
