package com.koshg.calendar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proposal_entries")
data class ProposalEntry(
    @PrimaryKey val id: String,
    /** ISO-8601 date, "yyyy-MM-dd". */
    val date: String,
    /** [Initiator.storageValue]. */
    val initiator: String,
    val accepted: Boolean,
    /** Only meaningful when [accepted] is false. */
    val declineReason: String,
    val notes: String
)
