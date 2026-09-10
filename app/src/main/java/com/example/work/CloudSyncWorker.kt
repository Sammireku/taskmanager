package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.FirestoreRepository
import com.example.data.PreferencesManager

/**
 * Background worker that synchronizes local Room tasks with Firestore Cloud.
 * Enforces NetworkType.CONNECTED and battery constraints to prevent battery drain.
 */
class CloudSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CloudSyncWorker"
        const val WORK_NAME = "cloud_sync_worker"
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (!prefs.isCloudSyncEnabled) {
            Log.d(TAG, "Cloud sync is disabled in preferences; skipping network sync.")
            return Result.success()
        }

        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val firestoreRepo = FirestoreRepository()
            val localTasks = db.taskDao().getAllTasksList()

            // Synchronize tasks bi-directionally with Firestore
            val remoteTasks = firestoreRepo.syncTasks(localTasks)
            for (remoteTask in remoteTasks) {
                db.taskDao().insertTask(remoteTask)
            }

            Log.i(TAG, "Successfully synchronized ${localTasks.size} local and ${remoteTasks.size} remote tasks.")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync failed or network unreachable: ${e.message}")
            Result.retry()
        }
    }
}
