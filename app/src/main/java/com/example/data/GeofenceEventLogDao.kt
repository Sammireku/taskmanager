package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceEventLogDao {
    @Query("SELECT * FROM geofence_event_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<GeofenceEventLog>>

    @Query("SELECT * FROM geofence_event_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getAllLogsList(): List<GeofenceEventLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: GeofenceEventLog): Long

    @Query("DELETE FROM geofence_event_logs")
    suspend fun clearAllLogs()
}
