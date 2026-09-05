package com.koshg.interlude.data

import org.json.JSONArray
import org.json.JSONObject

/** 2 since sex entries carry an orgasm count per person rather than one total. Nothing reads this
 *  on import -- [parseDataSnapshot] handles both shapes by key -- it only stamps what wrote the
 *  file. */
private const val EXPORT_VERSION = 2

/** A full snapshot of the app's own logged data -- not settings -- for manual export/import via
 *  a plain JSON file, independent of (and a supplement to) Android's own Auto Backup. */
data class DataSnapshot(
    val periods: List<PeriodEntry>,
    val events: List<CalendarEvent>,
    val sexEntries: List<SexEntry>,
    val proposalEntries: List<ProposalEntry>,
    val masturbationEntries: List<MasturbationEntry>
)

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

fun DataSnapshot.toExportJson(): String {
    val root = JSONObject()
    root.put("version", EXPORT_VERSION)

    root.put("periods", JSONArray(periods.map { entry ->
        JSONObject()
            .put("id", entry.id)
            .put("startDate", entry.startDate)
            .put("endDate", entry.endDate ?: JSONObject.NULL)
            .put("notes", entry.notes)
    }))

    root.put("events", JSONArray(events.map { entry ->
        JSONObject()
            .put("id", entry.id)
            .put("title", entry.title)
            .put("date", entry.date)
            .put("allDay", entry.allDay)
            .put("startTime", entry.startTime ?: JSONObject.NULL)
            .put("endTime", entry.endTime ?: JSONObject.NULL)
            .put("color", entry.color)
            .put("notes", entry.notes)
    }))

    root.put("sexEntries", JSONArray(sexEntries.map { entry ->
        JSONObject()
            .put("id", entry.id)
            .put("date", entry.date)
            .put("initiator", entry.initiator)
            .put("myOrgasmCount", entry.myOrgasmCount)
            .put("partnerOrgasmCount", entry.partnerOrgasmCount)
            .put("notes", entry.notes)
    }))

    root.put("proposalEntries", JSONArray(proposalEntries.map { entry ->
        JSONObject()
            .put("id", entry.id)
            .put("date", entry.date)
            .put("initiator", entry.initiator)
            .put("accepted", entry.accepted)
            .put("answered", entry.answered)
            .put("declineReason", entry.declineReason)
            .put("notes", entry.notes)
    }))

    root.put("masturbationEntries", JSONArray(masturbationEntries.map { entry ->
        JSONObject()
            .put("id", entry.id)
            .put("date", entry.date)
            .put("person", entry.person)
            .put("orgasmCount", entry.orgasmCount)
            .put("notes", entry.notes)
    }))

    return root.toString(2)
}

/** Parses a file produced by [toExportJson]. Every field is read defensively (a missing/malformed
 *  entry is skipped rather than aborting the whole import) since this file may have been hand-edited
 *  or come from a slightly different app version. */
fun parseDataSnapshot(json: String): DataSnapshot {
    val root = JSONObject(json)

    fun JSONObject.array(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    val periods = root.array("periods").mapNotNull { obj ->
        runCatching {
            PeriodEntry(
                id = obj.getString("id"),
                startDate = obj.getString("startDate"),
                endDate = obj.optNullableString("endDate"),
                notes = obj.optString("notes", "")
            )
        }.getOrNull()
    }

    val events = root.array("events").mapNotNull { obj ->
        runCatching {
            CalendarEvent(
                id = obj.getString("id"),
                title = obj.getString("title"),
                date = obj.getString("date"),
                allDay = obj.optBoolean("allDay", false),
                startTime = obj.optNullableString("startTime"),
                endTime = obj.optNullableString("endTime"),
                color = obj.optInt("color", EVENT_COLOR_PALETTE.first()),
                notes = obj.optString("notes", "")
            )
        }.getOrNull()
    }

    val sexEntries = root.array("sexEntries").mapNotNull { obj ->
        runCatching {
            SexEntry(
                id = obj.getString("id"),
                date = obj.getString("date"),
                initiator = obj.getString("initiator"),
                // A file written before orgasms were counted per person carries one total under
                // the old key; read it as this person's own, matching MIGRATION_4_5.
                myOrgasmCount = obj.optInt("myOrgasmCount", obj.optInt("orgasmCount", 0)),
                partnerOrgasmCount = obj.optInt("partnerOrgasmCount", 0),
                notes = obj.optString("notes", "")
            )
        }.getOrNull()
    }

    val proposalEntries = root.array("proposalEntries").mapNotNull { obj ->
        runCatching {
            ProposalEntry(
                id = obj.getString("id"),
                date = obj.getString("date"),
                initiator = obj.getString("initiator"),
                accepted = obj.optBoolean("accepted", true),
                answered = obj.optBoolean("answered", true),
                declineReason = obj.optString("declineReason", ""),
                notes = obj.optString("notes", "")
            )
        }.getOrNull()
    }

    val masturbationEntries = root.array("masturbationEntries").mapNotNull { obj ->
        runCatching {
            MasturbationEntry(
                id = obj.getString("id"),
                date = obj.getString("date"),
                person = obj.getString("person"),
                orgasmCount = obj.optInt("orgasmCount", 0),
                notes = obj.optString("notes", "")
            )
        }.getOrNull()
    }

    return DataSnapshot(periods, events, sexEntries, proposalEntries, masturbationEntries)
}
