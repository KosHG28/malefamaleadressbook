package com.koshg.interlude.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * Exists only to notice that Auto Backup ran.
 *
 * The manifest declares this with android:fullBackupOnly="true", so the system still does all the
 * actual file copying from res/xml/data_extraction_rules.xml -- the key/value [onBackup]/[onRestore]
 * pair below is never called and stays empty. What this buys is the two callbacks that *are*
 * called, which let the app stamp a timestamp it can show in Settings.
 *
 * Without it, Auto Backup is entirely unobservable from inside the app: it runs on the system's
 * own schedule (roughly daily, only while charging, idle and on an unmetered network) and there is
 * no API to query whether it has ever succeeded. "It doesn't work" and "it hasn't run yet" look
 * identical, which is exactly the confusion this removes.
 */
class CalendarBackupAgent : BackupAgent() {

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onFullBackup(data: FullBackupDataOutput?) {
        super.onFullBackup(data)
        // Stamped after the copy so it only records backups that actually completed. It lands in
        // backup_status_prefs, which the extraction rules exclude, so it describes this install
        // rather than travelling to the next one.
        BackupStatus(this).recordBackup()
    }

    override fun onRestoreFinished() {
        super.onRestoreFinished()
        BackupStatus(this).recordRestore()
    }
}
