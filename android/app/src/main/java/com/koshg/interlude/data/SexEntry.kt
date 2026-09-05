package com.koshg.interlude.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sex_entries")
data class SexEntry(
    @PrimaryKey val id: String,
    /** ISO-8601 date, "yyyy-MM-dd". */
    val date: String,
    /** [Initiator.storageValue]. */
    val initiator: String,
    /** Counted per person rather than as one total for the encounter: "3 orgasms" says nothing
     *  about whose they were, which is most of what makes the number worth logging. */
    val myOrgasmCount: Int,
    val partnerOrgasmCount: Int,
    val notes: String
)
