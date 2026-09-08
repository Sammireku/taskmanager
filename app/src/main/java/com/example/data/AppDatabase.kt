package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }
    }
}
