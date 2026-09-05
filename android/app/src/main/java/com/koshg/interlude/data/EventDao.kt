package com.koshg.interlude.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY date ASC, startTime ASC")
    fun getAllEvents(): Flow<List<CalendarEvent>>

    @Upsert
    suspend fun upsert(event: CalendarEvent)

    @Delete
    suspend fun delete(event: CalendarEvent)
}
