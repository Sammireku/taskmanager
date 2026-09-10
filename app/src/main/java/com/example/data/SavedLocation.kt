package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String, // e.g., "Home", "Work", "School", "Market", "Gym"
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val category: String = "CUSTOM" // "HOME", "WORK", "SCHOOL", "MARKET", "GYM", "CUSTOM"
) {
    val displayIcon: String
        get() = when (category.uppercase()) {
            "HOME" -> "🏠"
            "WORK" -> "💼"
            "SCHOOL" -> "🏫"
            "MARKET", "STORE" -> "🛒"
            "GYM" -> "🏋️"
            else -> "📍"
        }
}
