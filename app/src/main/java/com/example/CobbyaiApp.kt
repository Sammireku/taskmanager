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
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "cobbyai-database"
        )
        .fallbackToDestructiveMigration()
        .build()

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

