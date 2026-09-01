package com.koshg.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sex_entries")
data class SexEntry(
    @PrimaryKey val id: String,
    /** ISO-8601 date, "yyyy-MM-dd". */
    val date: String,
    /** [Initiator.storageValue]. */
    val initiator: String,
    val notes: String
)
