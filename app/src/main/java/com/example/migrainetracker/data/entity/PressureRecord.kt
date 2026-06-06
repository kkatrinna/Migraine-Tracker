package com.example.migrainetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "pressure_records")
data class PressureRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: LocalDate,
    val time: LocalTime,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int = 0
)