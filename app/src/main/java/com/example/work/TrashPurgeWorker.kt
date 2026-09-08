package com.example.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.Room
import com.example.data.AppDatabase

class TrashPurgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "cobbyai-database"
            ).addMigrations(AppDatabase.MIGRATION_5_6).fallbackToDestructiveMigration(true).build()

            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            db.taskDao().purgeOldTrash(thirtyDaysAgo)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
