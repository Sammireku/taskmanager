package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofence_event_logs")
data class GeofenceEventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val geofenceId: String = "",
    val taskTitle: String = "",
    val transitionType: String = "ENTER", // "ENTER", "DWELL", "EXIT", "CALIBRATION"
    val dwellDurationMs: Long? = null,
    val accuracyMeters: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val notes: String? = null
)
