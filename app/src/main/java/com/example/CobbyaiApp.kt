package com.example

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.notification.TaskAlarmReceiver
import com.google.android.gms.maps.MapsInitializer

class CobbyaiApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            val db = Room.databaseBuilder(
                this,
                AppDatabase::class.java,
                "cobbyai-database"
            )
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration(true)
            .build()

            // Open writable database to force Room migration validation during application launch
            db.openHelper.writableDatabase
            database = db
        } catch (e: Exception) {
            Log.e("CobbyaiApp", "Error initializing database: ${e.message}", e)
            try {
                deleteDatabase("cobbyai-database")
            } catch (_: Exception) {}

            val freshDb = Room.databaseBuilder(
                this,
                AppDatabase::class.java,
                "cobbyai-database"
            )
            .fallbackToDestructiveMigration(true)
            .build()

            try {
                freshDb.openHelper.writableDatabase
            } catch (_: Exception) {}

            database = freshDb
        }

        try {
            MapsInitializer.initialize(applicationContext)
        } catch (e: Exception) {
            Log.e("CobbyaiApp", "Failed to initialize MapsInitializer: ${e.message}")
        }

        try {
            TaskAlarmReceiver.initializeNotificationChannels(applicationContext)
        } catch (e: Exception) {
            Log.e("CobbyaiApp", "Failed to initialize notification channels: ${e.message}")
        }
    }
}

