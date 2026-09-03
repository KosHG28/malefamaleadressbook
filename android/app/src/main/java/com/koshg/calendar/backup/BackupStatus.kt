package com.koshg.calendar.backup

import android.content.Context

private const val PREFS_NAME = "backup_status_prefs"
private const val KEY_LAST_BACKUP_AT = "last_backup_at"
private const val KEY_LAST_RESTORE_AT = "last_restore_at"

/**
 * When Android last actually ran a cloud backup or a restore for this app.
 *
 * Deliberately kept in its own preferences file, and deliberately *not* listed in
 * res/xml/data_extraction_rules.xml: those rules are an allow-list, so an unlisted file is
 * excluded, and a restored backup therefore doesn't drag the old device's timestamps along with
 * it. "Last backup" always describes this install.
 *
 * The point is observability. Auto Backup is otherwise completely invisible from inside the app --
 * it runs on the system's schedule, and there's no API to ask whether it's working.
 */
class BackupStatus(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch millis of the last completed cloud backup, or null if one never ran on this install. */
    val lastBackupAtMillis: Long?
        get() = prefs.getLong(KEY_LAST_BACKUP_AT, 0L).takeIf { it > 0L }

    /** Epoch millis of the last restore into this install, or null if it was never restored. */
    val lastRestoreAtMillis: Long?
        get() = prefs.getLong(KEY_LAST_RESTORE_AT, 0L).takeIf { it > 0L }

    fun recordBackup(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, atMillis).apply()
    }

    fun recordRestore(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_RESTORE_AT, atMillis).apply()
    }
}
