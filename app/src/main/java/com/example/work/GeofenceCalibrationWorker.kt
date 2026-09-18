package com.example.work

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.GeofenceEventLog
import com.example.location.GeofenceManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Background worker to periodically calibrate geofence radii based on real-world GPS accuracy signals.
 * This prevents trigger failures when GPS accuracy is degraded (e.g. indoors, cloudy weather, urban canyons).
 */
class GeofenceCalibrationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "GeofenceCalibration"
        const val WORK_NAME = "periodic_geofence_calibration_work"
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            
            val lastLocation = try {
                val task = fusedClient.lastLocation
                Tasks.await(task, 5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                null
            }

            val accuracy = lastLocation?.accuracy ?: 30.0f
            val lat = lastLocation?.latitude ?: 0.0
            val lng = lastLocation?.longitude ?: 0.0

            Log.d(TAG, "Current GPS accuracy reading: ±${accuracy}m")

            val allTasks = db.taskDao().getAllTasksList()
            val geofencedTasks = allTasks.filter {
                it.latitude != null && it.longitude != null && !it.isDone && !it.isSoftDeleted
            }

            val geofenceManager = GeofenceManager(applicationContext)
            var calibratedCount = 0

            for (task in geofencedTasks) {
                // Minimum safe radius is at least 1.4x the GPS uncertainty margin, min 200m
                val safeMinimumRadius = max(200.0f, accuracy * 1.4f).coerceAtMost(600.0f)
                if (task.geofenceRadius < safeMinimumRadius) {
                    val updatedTask = task.copy(geofenceRadius = safeMinimumRadius)
                    db.taskDao().updateTask(updatedTask)
                    geofenceManager.registerTaskGeofence(updatedTask)
                    calibratedCount++

                    db.geofenceEventLogDao().insertLog(
                        GeofenceEventLog(
                            timestamp = System.currentTimeMillis(),
                            geofenceId = task.id.toString(),
                            taskTitle = task.safeTitle,
                            transitionType = "CALIBRATION",
                            accuracyMeters = accuracy,
                            latitude = lat,
                            longitude = lng,
                            notes = "Auto-calibrated radius from ${task.geofenceRadius.toInt()}m to ${safeMinimumRadius.toInt()}m (GPS accuracy ±${accuracy.toInt()}m)"
                        )
                    )
                }
            }

            Log.i(TAG, "Geofence calibration complete. Calibrated $calibratedCount tasks with GPS accuracy ±${accuracy}m.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error running geofence calibration: ${e.message}", e)
            Result.retry()
        }
    }
}
