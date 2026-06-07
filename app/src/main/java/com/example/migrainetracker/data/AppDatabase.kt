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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    version = 6,
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE migraine_records ADD COLUMN endTime TEXT")
                } catch (e: Exception) {
                }
                try {
                    database.execSQL("ALTER TABLE migraine_records ADD COLUMN nausea INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                }
                try {
                    database.execSQL("ALTER TABLE migraine_records ADD COLUMN photophobia INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                }
                try {
                    database.execSQL("ALTER TABLE migraine_records ADD COLUMN aura INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                }
                try {
                    database.execSQL("ALTER TABLE migraine_records ADD COLUMN notes TEXT")
                } catch (e: Exception) {
                }

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS medicine_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        medicineName TEXT NOT NULL,
                        reminderTime TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        repeatInterval INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "migraine_tracker_db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateTriggers(database.triggerDao())
                    }
                }
            }
        }

        private suspend fun populateTriggers(triggerDao: TriggerDao) {
            val existingTriggers = triggerDao.getAllTriggers()
            if (existingTriggers.isEmpty()) {
                val presetTriggers = listOf(
                    "Стресс", "Недосып", "Яркий свет", "Громкий звук",
                    "Погода", "Голод", "Кофеин", "Алкоголь", "Гормоны", "Другое"
                )
                for (triggerName in presetTriggers) {
                    triggerDao.insertTrigger(Trigger(name = triggerName))
                }
            }
        }
    }
}