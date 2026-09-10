package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY id ASC")
    fun getAllSavedLocations(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations ORDER BY id ASC")
    suspend fun getAllSavedLocationsList(): List<SavedLocation>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getSavedLocationById(id: Int): SavedLocation?

    @Query("SELECT * FROM saved_locations WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSavedLocationByName(name: String): SavedLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedLocation(location: SavedLocation): Long

    @Update
    suspend fun updateSavedLocation(location: SavedLocation)

    @Delete
    suspend fun deleteSavedLocation(location: SavedLocation)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: Int)
}
