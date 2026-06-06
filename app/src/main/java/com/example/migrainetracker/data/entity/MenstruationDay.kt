package com.example.migrainetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "menstruation_days")
data class MenstruationDay(
    @PrimaryKey
    val date: LocalDate,
    val isMenstruating: Boolean
)