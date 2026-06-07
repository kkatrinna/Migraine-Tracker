package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.TopTrigger
import com.example.migrainetracker.data.entity.Trigger

@Dao
interface TriggerDao {

    @Query("SELECT * FROM triggers ORDER BY name")
    suspend fun getAllTriggers(): List<Trigger>

    @Insert
    suspend fun insertTrigger(trigger: Trigger): Long

    @Delete
    suspend fun deleteTrigger(trigger: Trigger)

    @Query("DELETE FROM triggers")
    suspend fun deleteAllTriggers()

    @Query("""
        SELECT t.name, COUNT(mrt.triggerId) as count 
        FROM triggers t
        INNER JOIN migraine_record_triggers mrt ON t.id = mrt.triggerId
        GROUP BY t.id
        ORDER BY count DESC
        LIMIT 5
    """)
    suspend fun getTopTriggers(): List<TopTrigger>

    @Query("""
        SELECT t.name, COALESCE(COUNT(mrt.triggerId), 0) as count 
        FROM triggers t
        LEFT JOIN migraine_record_triggers mrt ON t.id = mrt.triggerId
        GROUP BY t.id
        HAVING count > 0
        ORDER BY count DESC
        LIMIT 5
    """)
    suspend fun getUsedTopTriggers(): List<TopTrigger>
}