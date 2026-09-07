package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class SubTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val description: String? = null,
    val priority: String = "Medium", // "High", "Medium", "Low"
    val dueDate: Long? = null, // Timestamp ms
    val completionStatus: String = "PENDING", // "PENDING", "IN_PROGRESS", "COMPLETED"
    val status: String = "PENDING", // "PENDING", "IN_PROGRESS", "COMPLETED"
    val isHabit: Boolean = false, // Habit tracking flag
    val habitFrequency: String? = null, // e.g. "Daily", "Weekly", "Weekdays"
    val isCompleted: Boolean = false,
    val category: String? = null,
    val subtasksJson: String? = null, // JSON serialized List<SubTask>
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadius: Float = 150f, // in meters
    val triggerDirection: String = "ARRIVAL", // "ARRIVAL" or "DEPARTURE"
    val reminderTone: String? = "DEFAULT", // "DEFAULT", "URGENT_ALARM", "GENTLE_NOTIF", "PHONE_RINGTONE", "CHIME", "BEACON"
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) {
    val isSoftDeleted: Boolean
        get() = isDeleted || (deletedAt != null && deletedAt!! > 0)

    val isDone: Boolean
        get() = isCompleted || completionStatus == "COMPLETED" || safeStatus == "COMPLETED"

    val safeTitle: String
        get() = (title as String?).orEmpty().ifBlank { "Untitled Task" }

    val safePriority: String
        get() = (priority as String?).orEmpty().ifBlank { "Medium" }

    val safeStatus: String
        get() = (status as String?).orEmpty().ifBlank { "PENDING" }

    val safeCategory: String
        get() = (category as String?).orEmpty().ifBlank { "General" }

    val safeTriggerDirection: String
        get() = (triggerDirection as String?).orEmpty().ifBlank { "ARRIVAL" }

    val safeReminderTone: String
        get() = (reminderTone as String?).orEmpty().ifBlank { "DEFAULT" }
}
