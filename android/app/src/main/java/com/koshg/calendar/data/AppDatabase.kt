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
    version = 3,
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

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calendar.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
