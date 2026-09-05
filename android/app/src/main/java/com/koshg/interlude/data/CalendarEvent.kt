package com.koshg.interlude.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class CalendarEvent(
    @PrimaryKey val id: String,
    val title: String,
    /** ISO-8601 date, "yyyy-MM-dd". */
    val date: String,
    val allDay: Boolean,
    /** "HH:mm", null when [allDay] is true. */
    val startTime: String?,
    /** "HH:mm", null when [allDay] is true. */
    val endTime: String?,
    val color: Int,
    val notes: String
)

val EVENT_COLOR_PALETTE: List<Int> = listOf(
    0xFF5B8DEF.toInt(),
    0xFFE0574C.toInt(),
    0xFF39B370.toInt(),
    0xFFE6A23C.toInt(),
    0xFF9B59B6.toInt(),
    0xFF33BDCA.toInt(),
)
