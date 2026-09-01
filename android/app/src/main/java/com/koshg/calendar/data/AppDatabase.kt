package com.koshg.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CalendarEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calendar.db"
                ).build().also { INSTANCE = it }
            }
    }
}
