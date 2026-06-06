package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.MenstruationDay
import java.time.LocalDate

@Dao
interface MenstruationDayDao {

    @Query("SELECT * FROM menstruation_days WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getDaysInRange(startDate: LocalDate, endDate: LocalDate): List<MenstruationDay>

    @Query("SELECT * FROM menstruation_days WHERE isMenstruating = 1 ORDER BY date")
    suspend fun getAllMenstruationDays(): List<MenstruationDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(day: MenstruationDay)

    @Query("SELECT date FROM menstruation_days WHERE isMenstruating = 1 ORDER BY date DESC LIMIT 1")
    suspend fun getLastMenstruationDate(): LocalDate?

    @Query("DELETE FROM menstruation_days WHERE date = :date")
    suspend fun delete(date: LocalDate)

    @Query("DELETE FROM menstruation_days")
    suspend fun deleteAll()
}