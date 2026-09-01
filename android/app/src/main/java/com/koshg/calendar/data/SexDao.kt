package com.koshg.calendar.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SexDao {
    @Query("SELECT * FROM sex_entries ORDER BY date DESC")
    fun getAll(): Flow<List<SexEntry>>

    @Upsert
    suspend fun upsert(entry: SexEntry)

    @Delete
    suspend fun delete(entry: SexEntry)
}
