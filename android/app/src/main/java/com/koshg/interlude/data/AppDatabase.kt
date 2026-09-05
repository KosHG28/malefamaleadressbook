package com.koshg.interlude.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CalendarEvent::class,
        PeriodEntry::class,
        SexEntry::class,
        ProposalEntry::class,
        MasturbationEntry::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun periodDao(): PeriodDao
    abstract fun sexDao(): SexDao
    abstract fun proposalDao(): ProposalDao
    abstract fun masturbationDao(): MasturbationDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `period_entries` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `startDate` TEXT NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sex_entries` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `date` TEXT NOT NULL,
                        `initiator` TEXT NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `proposal_entries` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `date` TEXT NOT NULL,
                        `initiator` TEXT NOT NULL,
                        `accepted` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sex_entries` ADD COLUMN `orgasmCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `proposal_entries` ADD COLUMN `declineReason` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `masturbation_entries` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `date` TEXT NOT NULL,
                        `person` TEXT NOT NULL,
                        `orgasmCount` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `period_entries` ADD COLUMN `endDate` TEXT")
                db.execSQL("ALTER TABLE `proposal_entries` ADD COLUMN `answered` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The old single orgasmCount became this person's own count. That is a guess for
                // rows logged before the split, but the useful one: the entry was written from
                // this device, by the person using it.
                db.execSQL("ALTER TABLE `sex_entries` RENAME COLUMN `orgasmCount` TO `myOrgasmCount`")
                db.execSQL("ALTER TABLE `sex_entries` ADD COLUMN `partnerOrgasmCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // declineReason used to hold the Russian chip label itself. Now it holds a stable
                // code (see DeclineReason), so that a fatigue pattern is still recognised after
                // the user switches language. Map the three labels that were ever offered as
                // chips; anything else was typed by hand and stays as the free text it is.
                db.execSQL(
                    "UPDATE `proposal_entries` SET `declineReason` = 'fatigue' " +
                        "WHERE `declineReason` = 'Усталость'"
                )
                db.execSQL(
                    "UPDATE `proposal_entries` SET `declineReason` = 'mood' " +
                        "WHERE `declineReason` = 'Настроение'"
                )
                db.execSQL(
                    "UPDATE `proposal_entries` SET `declineReason` = 'wellbeing' " +
                        "WHERE `declineReason` = 'Самочувствие'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calendar.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
                    )
                    // TRUNCATE (not the default WAL) so every commit lands fully in calendar.db
                    // itself, with no separate -wal/-shm sidecar file Android's Auto Backup
                    // (a plain file copy, no SQLite awareness) could snapshot mid-write.
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
