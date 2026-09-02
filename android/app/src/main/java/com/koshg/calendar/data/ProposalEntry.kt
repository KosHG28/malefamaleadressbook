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
    /** Only meaningful when [answered] is true -- an unanswered proposal has neither been
     *  accepted nor declined yet. */
    val accepted: Boolean,
    /** False for a proposal that was made but hasn't been resolved yet ("Ожидает ответа").
     *  Defaults to true so every proposal logged before this field existed reads as already
     *  answered, matching how the app behaved when there was no third state. */
    val answered: Boolean = true,
    /** Only meaningful when [answered] is true and [accepted] is false. */
    val declineReason: String,
    val notes: String
)
