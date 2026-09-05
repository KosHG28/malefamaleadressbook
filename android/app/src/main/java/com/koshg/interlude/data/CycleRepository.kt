package com.koshg.interlude.data

import kotlinx.coroutines.flow.Flow

class CycleRepository(private val dao: PeriodDao) {
    val periods: Flow<List<PeriodEntry>> = dao.getAll()

    suspend fun save(entry: PeriodEntry) = dao.upsert(entry)

    suspend fun delete(entry: PeriodEntry) = dao.delete(entry)
}
