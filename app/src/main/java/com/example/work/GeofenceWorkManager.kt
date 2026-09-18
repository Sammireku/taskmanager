package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object GeofenceWorkManager {
    private const val TAG = "GeofenceWorkManager"
    private const val PERIODIC_WORK_NAME = "cobbyai_periodic_geofence_sync"

    /**
     * Enqueues a WorkManager request for an incoming geofence transition.
     * Replaces raw JobInfo implementations to ensure compliance with modern
     * Android background execution limits and maximum battery efficiency.
     */
    fun enqueueGeofenceEvent(
        context: Context,
        requestId: String,
        transitionType: Int,
        latitude: Double,
        longitude: Double
    ) {
        try {
            val inputData = Data.Builder()
                .putString(GeofenceBackgroundWorker.KEY_REQUEST_ID, requestId)
                .putInt(GeofenceBackgroundWorker.KEY_TRANSITION_TYPE, transitionType)
                .putDouble(GeofenceBackgroundWorker.KEY_LATITUDE, latitude)
                .putDouble(GeofenceBackgroundWorker.KEY_LONGITUDE, longitude)
                .build()

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GeofenceBackgroundWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "geofence_event_${requestId}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.i(TAG, "Enqueued WorkManager task for geofence event: $requestId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue GeofenceBackgroundWorker: ${e.message}", e)
        }
    }

    /**
     * Schedules periodic background geofence state verification every 15 minutes.
     */
    fun schedulePeriodicGeofenceSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<GeofenceBackgroundWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.i(TAG, "Scheduled periodic WorkManager geofence sync.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic geofence sync: ${e.message}", e)
        }
    }
}
