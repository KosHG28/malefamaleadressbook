package com.koshg.interlude.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MasturbationDao {
    @Query("SELECT * FROM masturbation_entries ORDER BY date DESC")
    fun getAll(): Flow<List<MasturbationEntry>>

    @Upsert
    suspend fun upsert(entry: MasturbationEntry)

    @Delete
    suspend fun delete(entry: MasturbationEntry)
}
