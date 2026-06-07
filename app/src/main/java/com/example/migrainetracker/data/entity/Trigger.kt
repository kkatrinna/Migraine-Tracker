package com.example.migrainetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "triggers")
data class Trigger(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
)