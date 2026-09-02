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
    /** ISO-8601 date, "yyyy-MM-dd" — the last day of bleeding, inclusive. Null when only the
     *  start was logged (the common case): the calendar then falls back to an assumed duration
     *  (see [com.koshg.calendar.util.ASSUMED_PERIOD_DURATION_DAYS]) rather than treating an
     *  unentered end as "still ongoing". */
    val endDate: String? = null,
    val notes: String
)
