package com.example.migrainetracker.data.repository

import com.example.migrainetracker.data.AppDatabase
import com.example.migrainetracker.data.entity.*
import java.time.LocalDate

data class DayCardData(
    val date: LocalDate,
    val maxIntensity: Int?,
    val isMenstruating: Boolean,
    val avgPressure: Double?,
    val avgPulse: Double?
)

class TrackerRepository(private val db: AppDatabase) {

    suspend fun getMigraineRecordsForDay(date: LocalDate): List<MigraineRecord> {
        return db.migraineRecordDao().getRecordsForDay(date)
    }

    suspend fun addMigraineRecord(record: MigraineRecord) {
        db.migraineRecordDao().insert(record)
    }

    suspend fun deleteMigraineRecord(record: MigraineRecord) {
        db.migraineRecordDao().delete(record)
    }


    suspend fun getMigraineRecordsForDateRange(startDate: LocalDate, endDate: LocalDate): List<MigraineRecord> {
        return db.migraineRecordDao().getRecordsForDateRange(startDate, endDate)
    }

    suspend fun getMenstruationDaysForMonth(startDate: LocalDate, endDate: LocalDate): List<MenstruationDay> {
        return db.menstruationDayDao().getDaysInRange(startDate, endDate)
    }


    suspend fun addPressureRecord(record: PressureRecord) {
        db.pressureRecordDao().insert(record)
    }

    suspend fun getPressureRecords(): List<PressureRecord> {
        return db.pressureRecordDao().getRecentRecords()
    }

    suspend fun deletePressureRecord(record: PressureRecord) {
        db.pressureRecordDao().delete(record)
    }

    suspend fun addPulseRecord(record: PulseRecord) {
        db.pulseRecordDao().insert(record)
    }

    suspend fun getPulseRecords(): List<PulseRecord> {
        return db.pulseRecordDao().getRecentRecords()
    }

    suspend fun deletePulseRecord(record: PulseRecord) {
        db.pulseRecordDao().delete(record)
    }

    suspend fun updateMigraineRecord(record: MigraineRecord) {
        db.migraineRecordDao().update(record)
    }


    suspend fun getDayCardData(date: LocalDate): DayCardData {
        val maxIntensity = db.migraineRecordDao().getMaxIntensityForDay(date)
        val isMenstruating = db.menstruationDayDao().getDaysInRange(date, date)
            .firstOrNull()?.isMenstruating ?: false
        val avgPressure = db.pressureRecordDao().getAveragePressureForDay(date)
        val avgPulse = db.pulseRecordDao().getAveragePulseForDay(date)

        return DayCardData(
            date = date,
            maxIntensity = maxIntensity,
            isMenstruating = isMenstruating,
            avgPressure = avgPressure,
            avgPulse = avgPulse
        )
    }
}