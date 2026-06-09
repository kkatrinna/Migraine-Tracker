package com.example.migrainetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "migraine_record_triggers")
data class MigraineRecordTrigger(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val recordId: Int,
    val triggerId: Int
)