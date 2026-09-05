package com.koshg.interlude.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {
    @Query("SELECT * FROM period_entries ORDER BY startDate DESC")
    fun getAll(): Flow<List<PeriodEntry>>

    @Upsert
    suspend fun upsert(entry: PeriodEntry)

    @Delete
    suspend fun delete(entry: PeriodEntry)
}
