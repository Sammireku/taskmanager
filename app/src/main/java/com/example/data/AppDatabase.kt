package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Task::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
