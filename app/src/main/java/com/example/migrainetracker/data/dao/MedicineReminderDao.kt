package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.MedicineReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineReminderDao {
    @Query("SELECT * FROM medicine_reminders ORDER BY reminderTime")
    fun getAllReminders(): Flow<List<MedicineReminder>>

    @Insert
    suspend fun insertReminder(reminder: MedicineReminder):Long

    @Update
    suspend fun updateReminder(reminder: MedicineReminder)

    @Delete
    suspend fun deleteReminder(reminder: MedicineReminder)

    @Query("SELECT * FROM medicine_reminders WHERE isEnabled = 1")
    suspend fun getEnabledReminders(): List<MedicineReminder>

    @Query("SELECT * FROM medicine_reminders ORDER BY reminderTime")
    suspend fun getAllRemindersList(): List<MedicineReminder>


}