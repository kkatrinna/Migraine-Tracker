package com.example.migrainetracker.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.migrainetracker.data.entity.MigraineRecordTrigger

@Dao
interface MigraineRecordTriggerDao {
    @Insert
    suspend fun insert(link: MigraineRecordTrigger)

    @Query("SELECT triggerId FROM migraine_record_triggers WHERE recordId = :recordId")
    suspend fun getTriggerIdsForRecord(recordId: Int): List<Int>

    @Delete
    suspend fun delete(link: MigraineRecordTrigger)

    @Query("DELETE FROM migraine_record_triggers WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: Int)
}