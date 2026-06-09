package com.example.migrainetracker.data

import android.content.Context
import android.util.Log
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
        private const val TAG = "AppDatabase"
        private const val DATABASE_NAME = "migraine_tracker_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Исправленная миграция 11->12 с проверкой существования таблицы
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating from version 11 to 12")
                try {
                    // Проверяем существует ли таблица trigger_table
                    val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='trigger_table'")
                    val tableExists = cursor.moveToFirst()
                    cursor.close()

                    if (tableExists) {
                        database.execSQL("""
                            DELETE FROM trigger_table 
                            WHERE id NOT IN (
                                SELECT MIN(id) 
                                FROM trigger_table 
                                GROUP BY name
                            )
                        """)
                        Log.d(TAG, "Migration 11->12 completed successfully")
                    } else {
                        Log.d(TAG, "Table trigger_table doesn't exist, skipping migration")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 11->12 failed", e)
                    // Не выбрасываем исключение, чтобы приложение не падало
                }
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating from version 10 to 11")
                try {
                    // Проверяем и создаем таблицу medicine_reminders если её нет
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `medicine_reminders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `medicineName` TEXT NOT NULL,
                            `reminderTime` TEXT NOT NULL,
                            `repeatInterval` INTEGER NOT NULL,
                            `isEnabled` INTEGER NOT NULL
                        )
                    """)
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 10->11 failed", e)
                }
            }
        }

        private val DATABASE_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "Database created, populating initial data")
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "Database opened")
            }
        }

        private suspend fun populateInitialData(database: AppDatabase) {
            try {
                val triggerDao = database.triggerDao()
                val existingTriggers = triggerDao.getAllTriggers()

                if (existingTriggers.isEmpty()) {
                    val presetTriggers = listOf(
                        "Стресс", "Недосып", "Яркий свет", "Громкий звук",
                        "Погода", "Голод", "Кофеин", "Алкоголь", "Гормоны", "Другое"
                    )
                    for (name in presetTriggers) {
                        triggerDao.insertTrigger(Trigger(name = name))
                    }
                    Log.d(TAG, "Initial triggers populated successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error populating initial data", e)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration() // Добавляем это для безопасности
                    .addCallback(DATABASE_CALLBACK)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}