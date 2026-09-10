package com.example.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.Task
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManager(private val context: Context) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    companion object {
        private const val TAG = "GeofenceManager"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_LOCATION_NAME = "extra_location_name"
    }

    private fun getPendingIntent(task: Task): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = "com.example.cobbyai.ACTION_GEOFENCE_TRIGGER"
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_LOCATION_NAME, task.locationName ?: "Saved Location")
        }
        return PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun registerTaskGeofence(task: Task) {
        val lat = task.latitude ?: return
        val lng = task.longitude ?: return
        val radius = task.geofenceRadius.coerceIn(100f, 5000f)

        val isDeparture = task.safeTriggerDirection.equals("DEPARTURE", ignoreCase = true)
        val transitionTypes = if (isDeparture) {
            Geofence.GEOFENCE_TRANSITION_EXIT
        } else {
            Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL
        }

        val geofence = Geofence.Builder()
            .setRequestId(task.id.toString())
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitionTypes)
            .setLoiteringDelay(30000) // 30 seconds dwell time verification
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                if (isDeparture) {
                    0 // Do not trigger immediately on task creation if currently outside
                } else {
                    GeofencingRequest.INITIAL_TRIGGER_ENTER
                }
            )
            .addGeofence(geofence)
            .build()

        try {
            val pendingIntent = getPendingIntent(task)
            geofencingClient.addGeofences(request, pendingIntent).run {
                addOnSuccessListener {
                    Log.d(TAG, "Successfully registered geofence for task ${task.id} with radius ${radius}m")
                }
                addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register geofence for task ${task.id}", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering geofence. Location permission missing?", e)
        }
    }

    fun removeTaskGeofence(taskId: Int) {
        try {
            geofencingClient.removeGeofences(listOf(taskId.toString())).run {
                addOnSuccessListener {
                    Log.d(TAG, "Geofence removed for task $taskId")
                }
                addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove geofence for task $taskId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing geofence", e)
        }
    }
}
