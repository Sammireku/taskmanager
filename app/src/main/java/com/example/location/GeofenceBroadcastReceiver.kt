package com.example.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.CobbyaiApp
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.GeofenceEventLog
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val CHANNEL_ID = "proximity_task_channel"
        const val CHANNEL_NAME = "Proximity Task Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "GeofenceBroadcastReceiver received null GeofencingEvent from intent: $intent")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing event error code: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        val transitionName = when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN ($geofenceTransition)"
        }
        Log.i(TAG, "Geofence event triggered! Transition: $transitionName, geofences count: ${triggeringGeofences.size}")
        triggeringGeofences.forEach { gf ->
            Log.i(TAG, "Triggered geofence requestId: ${gf.requestId}")
        }
        val firstGeofence = triggeringGeofences.firstOrNull()
        val taskId = firstGeofence?.requestId ?: intent.getIntExtra(GeofenceManager.EXTRA_TASK_ID, 0).toString()

        val taskTitle = intent.getStringExtra(GeofenceManager.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val locationName = intent.getStringExtra(GeofenceManager.EXTRA_LOCATION_NAME) ?: "Nearby Location"

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val taskIdInt = taskId.toIntOrNull() ?: intent.getIntExtra(GeofenceManager.EXTRA_TASK_ID, 0)
                val task = db.taskDao().getTaskById(taskIdInt)
                if (task != null && task.isDone) {
                    Log.d(TAG, "Task $taskId is already completed, skipping proximity alert.")
                    return@launch
                }
                val resolvedTitle = task?.safeTitle ?: taskTitle
                val resolvedLocation = task?.locationName ?: locationName

                val triggeringLoc = geofencingEvent.triggeringLocation
                val accuracy = triggeringLoc?.accuracy ?: 0f
                val lat = triggeringLoc?.latitude ?: task?.latitude ?: 0.0
                val lng = triggeringLoc?.longitude ?: task?.longitude ?: 0.0

                val isDwell = geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL
                val isExit = geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT

                // Record into GeofenceEventLog for diagnostics
                db.geofenceEventLogDao().insertLog(
                    com.example.data.GeofenceEventLog(
                        timestamp = System.currentTimeMillis(),
                        geofenceId = taskId,
                        taskTitle = resolvedTitle,
                        transitionType = transitionName,
                        dwellDurationMs = if (isDwell) 30000L else null,
                        accuracyMeters = accuracy,
                        latitude = lat,
                        longitude = lng,
                        notes = "Geofence triggered at $resolvedLocation (accuracy: ±${accuracy.toInt()}m)"
                    )
                )

                val notificationTitle = when {
                    isDwell -> "⏱️ Dwelling at $resolvedLocation"
                    isExit -> "🛫 Leaving $resolvedLocation"
                    else -> "📍 Arrived at $resolvedLocation"
                }

                val notificationBody = when {
                    isDwell -> "You've been at $resolvedLocation: Don't forget: $resolvedTitle"
                    isExit -> "Don't forget before you leave: $resolvedTitle"
                    else -> "You are right here! Time to complete: $resolvedTitle"
                }

                sendNotification(context, taskId.hashCode(), notificationTitle, notificationBody)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking task database for geofence event", e)
                val isExit = geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
                val notificationTitle = if (isExit) "🛫 Leaving $locationName" else "📍 Arrived at $locationName"
                val notificationBody = if (isExit) "Don't forget: $taskTitle" else "You are right here! Time to complete: $taskTitle"
                sendNotification(context, taskId.hashCode(), notificationTitle, notificationBody)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendNotification(context: Context, notifId: Int, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when you arrive or leave proximity-aware task locations"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notifId, notification)
    }
}
