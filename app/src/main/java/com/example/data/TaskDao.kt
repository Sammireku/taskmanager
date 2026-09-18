package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY (status = 'COMPLETED') ASC, dueDate ASC, id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAllTasksList(): List<Task>

    @Query("SELECT * FROM tasks WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND status != 'COMPLETED' AND deletedAt IS NULL")
    suspend fun getGeofencedTasks(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE tasks SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTask(id: Int, timestamp: Long)

    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreTask(id: Int)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoffTimestamp")
    suspend fun purgeOldTrash(cutoffTimestamp: Long)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Delete
    suspend fun deleteTask(task: Task)
}
