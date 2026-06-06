package com.example.migrainetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "migraine_records")
data class MigraineRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: LocalDate,
    val time: LocalTime,
    val intensity: Int,
    val medicationName: String?,
    val medicationTime: LocalTime?
)