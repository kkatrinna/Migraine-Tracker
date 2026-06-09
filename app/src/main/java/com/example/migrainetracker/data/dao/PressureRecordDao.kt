package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.PressureRecord
import java.time.LocalDate

@Dao
interface PressureRecordDao {

    @Query("SELECT * FROM pressure_records ORDER BY date DESC, time DESC LIMIT 50")
    suspend fun getRecentRecords(): List<PressureRecord>

    @Query("SELECT * FROM pressure_records WHERE date = :date ORDER BY time DESC")
    suspend fun getRecordsForDay(date: LocalDate): List<PressureRecord>

    @Insert
    suspend fun insert(record: PressureRecord)

    @Delete
    suspend fun delete(record: PressureRecord)

    @Query("SELECT AVG((systolic + diastolic) / 2.0) FROM pressure_records WHERE date = :date")
    suspend fun getAveragePressureForDay(date: LocalDate): Double?

    @Query("DELETE FROM pressure_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM pressure_records ORDER BY date DESC, time DESC")
    suspend fun getAllRecords(): List<PressureRecord>
}