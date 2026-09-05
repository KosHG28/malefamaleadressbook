package com.koshg.interlude.data

import kotlinx.coroutines.flow.Flow

class EventRepository(private val dao: EventDao) {
    val allEvents: Flow<List<CalendarEvent>> = dao.getAllEvents()

    suspend fun save(event: CalendarEvent) = dao.upsert(event)

    suspend fun delete(event: CalendarEvent) = dao.delete(event)
}
