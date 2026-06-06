package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.MigraineRecord
import java.time.LocalDate

@Dao
interface MigraineRecordDao {

    @Query("SELECT * FROM migraine_records WHERE date = :date ORDER BY time")
    suspend fun getRecordsForDay(date: LocalDate): List<MigraineRecord>

    @Query("SELECT * FROM migraine_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date, time")
    suspend fun getRecordsForDateRange(startDate: LocalDate, endDate: LocalDate): List<MigraineRecord>

    @Query("SELECT MAX(intensity) FROM migraine_records WHERE date = :date")
    suspend fun getMaxIntensityForDay(date: LocalDate): Int?

    @Insert
    suspend fun insert(record: MigraineRecord)

    @Delete
    suspend fun delete(record: MigraineRecord)

    @Query("SELECT * FROM migraine_records ORDER BY date DESC, time DESC LIMIT 50")
    suspend fun getRecentRecords(): List<MigraineRecord>

    @Query("DELETE FROM migraine_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM migraine_records ORDER BY date DESC, time DESC")
    suspend fun getAllRecords(): List<MigraineRecord>

    @Update
    suspend fun update(record: MigraineRecord)
}