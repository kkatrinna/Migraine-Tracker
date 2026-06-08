package com.example.migrainetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.migrainetracker.data.dao.*
import com.example.migrainetracker.data.entity.*
import com.example.migrainetracker.utils.DateConverters

@Database(
    entities = [
        MigraineRecord::class,
        MenstruationDay::class,
        PressureRecord::class,
        PulseRecord::class,
        Trigger::class,
        MigraineRecordTrigger::class,
        MedicineReminder::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun migraineRecordDao(): MigraineRecordDao
    abstract fun menstruationDayDao(): MenstruationDayDao
    abstract fun pressureRecordDao(): PressureRecordDao
    abstract fun pulseRecordDao(): PulseRecordDao
    abstract fun triggerDao(): TriggerDao
    abstract fun migraineRecordTriggerDao(): MigraineRecordTriggerDao
    abstract fun medicineReminderDao(): MedicineReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "migraine_tracker_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}