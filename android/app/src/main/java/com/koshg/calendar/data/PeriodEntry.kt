package com.koshg.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A manually logged menstrual period start date. Ovulation, the fertile window and
 * predictions are derived from these (see util/CycleCalculator.kt) — nothing else
 * about the cycle is entered by hand.
 */
@Entity(tableName = "period_entries")
data class PeriodEntry(
    @PrimaryKey val id: String,
    /** ISO-8601 date, "yyyy-MM-dd". */
    val startDate: String,
    val notes: String
)
