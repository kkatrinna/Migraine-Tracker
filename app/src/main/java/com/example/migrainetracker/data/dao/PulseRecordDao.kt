package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.PulseRecord
import java.time.LocalDate

@Dao
interface PulseRecordDao {

    @Query("SELECT * FROM pulse_records ORDER BY date DESC, time DESC LIMIT 50")
    suspend fun getRecentRecords(): List<PulseRecord>

    @Query("SELECT * FROM pulse_records WHERE date = :date ORDER BY time DESC")
    suspend fun getRecordsForDay(date: LocalDate): List<PulseRecord>

    @Insert
    suspend fun insert(record: PulseRecord)

    @Delete
    suspend fun delete(record: PulseRecord)

    @Query("SELECT AVG(pulse) FROM pulse_records WHERE date = :date")
    suspend fun getAveragePulseForDay(date: LocalDate): Double?

    @Query("DELETE FROM pulse_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM pulse_records ORDER BY date DESC, time DESC")
    suspend fun getAllRecords(): List<PulseRecord>
}

