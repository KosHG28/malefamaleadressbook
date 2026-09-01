package com.koshg.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "masturbation_entries")
data class MasturbationEntry(
    @PrimaryKey val id: String,
    /** ISO-8601 date, "yyyy-MM-dd". */
    val date: String,
    /** [Initiator.storageValue] — who. */
    val person: String,
    val orgasmCount: Int,
    val notes: String
)
