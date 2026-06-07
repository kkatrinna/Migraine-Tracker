package com.example.migrainetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "medicine_reminders")
data class MedicineReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val medicineName: String,
    val reminderTime: LocalTime,
    val isEnabled: Boolean = true,
    val repeatInterval: Int = 0
)