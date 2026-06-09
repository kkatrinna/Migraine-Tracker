package com.example.migrainetracker.data.dao

import androidx.room.*
import com.example.migrainetracker.data.entity.TopTrigger
import com.example.migrainetracker.data.entity.Trigger

@Dao
interface TriggerDao {

    @Query("SELECT * FROM triggers ORDER BY name ASC")
    suspend fun getAllTriggers(): List<Trigger>

    @Query("SELECT * FROM triggers GROUP BY name ORDER BY name ASC")
    suspend fun getAllTriggersDistinct(): List<Trigger>

    @Insert
    suspend fun insertTrigger(trigger: Trigger): Long

    @Delete
    suspend fun deleteTrigger(trigger: Trigger)

    @Query("DELETE FROM triggers")
    suspend fun deleteAllTriggers()

    @Query("DELETE FROM triggers WHERE id NOT IN (SELECT MIN(id) FROM triggers GROUP BY name)")
    suspend fun deleteDuplicateTriggers()

    @Query("""
        SELECT t.name, COUNT(mrt.triggerId) as count 
        FROM triggers t
        INNER JOIN migraine_record_triggers mrt ON t.id = mrt.triggerId
        GROUP BY t.name
        ORDER BY count DESC
        LIMIT 3
    """)
    suspend fun getTopTriggers(): List<TopTrigger>

    @Query("""
        SELECT t.name, COALESCE(COUNT(mrt.triggerId), 0) as count 
        FROM triggers t
        LEFT JOIN migraine_record_triggers mrt ON t.id = mrt.triggerId
        GROUP BY t.name
        HAVING count > 0
        ORDER BY count DESC
        LIMIT 3
    """)
    suspend fun getUsedTopTriggers(): List<TopTrigger>
}