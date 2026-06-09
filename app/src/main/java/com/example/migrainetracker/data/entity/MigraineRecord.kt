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
    val endTime: LocalTime? = null,
    val intensity: Int,
    val medicationName: String? = null,
    val medicationTime: LocalTime? = null,
    val nausea: Boolean = false,
    val photophobia: Boolean = false,
    val aura: Boolean = false,
    val notes: String? = null
)