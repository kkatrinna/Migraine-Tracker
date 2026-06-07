package com.example.migrainetracker.data.entity

import androidx.room.ColumnInfo

data class TopTrigger(
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "count")
    val count: Int
)