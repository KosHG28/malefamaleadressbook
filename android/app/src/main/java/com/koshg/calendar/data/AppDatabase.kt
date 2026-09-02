package com.koshg.calendar.data

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
    version = 4,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calendar.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // TRUNCATE (not the default WAL) so every commit lands fully in calendar.db
                    // itself, with no separate -wal/-shm sidecar file Android's Auto Backup
                    // (a plain file copy, no SQLite awareness) could snapshot mid-write.
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
