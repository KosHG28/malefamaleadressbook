package com.koshg.interlude.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProposalDao {
    @Query("SELECT * FROM proposal_entries ORDER BY date DESC")
    fun getAll(): Flow<List<ProposalEntry>>

    @Upsert
    suspend fun upsert(entry: ProposalEntry)

    @Delete
    suspend fun delete(entry: ProposalEntry)
}
