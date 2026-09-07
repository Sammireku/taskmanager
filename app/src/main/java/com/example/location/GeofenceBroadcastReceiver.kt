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
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val CHANNEL_ID = "proximity_task_channel"
        const val CHANNEL_NAME = "Proximity Task Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error code: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        val firstGeofence = triggeringGeofences.firstOrNull()
        val taskId = firstGeofence?.requestId ?: intent.getIntExtra(GeofenceManager.EXTRA_TASK_ID, 0).toString()

        val taskTitle = intent.getStringExtra(GeofenceManager.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val locationName = intent.getStringExtra(GeofenceManager.EXTRA_LOCATION_NAME) ?: "Nearby Location"

        val isExit = geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT
        val notificationTitle = if (isExit) {
            "🛫 Leaving $locationName"
        } else {
            "📍 Arrived at $locationName"
        }

        val notificationBody = if (isExit) {
            "Don't forget: $taskTitle"
        } else {
            "You are right here! Time to complete: $taskTitle"
        }

        sendNotification(context, taskId.hashCode(), notificationTitle, notificationBody)
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
