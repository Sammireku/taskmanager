package com.example.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.GeofenceEventLog
import com.example.notification.AlarmRingtonePlayer
import com.example.notification.ReminderAlarmActivity
import com.example.notification.TaskActionReceiver

class GeofenceBackgroundWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "GeofenceBackgroundWorker"
        const val CHANNEL_ID = "proximity_task_channel_alarm_v2"
        const val CHANNEL_NAME = "Proximity Task Geofence Alerts"

        const val KEY_REQUEST_ID = "key_request_id"
        const val KEY_TRANSITION_TYPE = "key_transition_type"
        const val KEY_LATITUDE = "key_latitude"
        const val KEY_LONGITUDE = "key_longitude"
    }

    override suspend fun doWork(): Result {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Cobbyai:GeofenceWorkerWakeLock"
        )
        wakeLock.acquire(10000L) // 10s timeout safety guard

        return try {
            val requestId = inputData.getString(KEY_REQUEST_ID) ?: ""
            val transitionType = inputData.getInt(KEY_TRANSITION_TYPE, 1) // 1 = ENTER
            val lat = inputData.getDouble(KEY_LATITUDE, 0.0)
            val lng = inputData.getDouble(KEY_LONGITUDE, 0.0)

            Log.i(TAG, "GeofenceBackgroundWorker executing for requestId: $requestId, transition: $transitionType")

            val db = AppDatabase.getInstance(context)

            // Periodic Sync mode if requestId is empty
            if (requestId.isBlank()) {
                Log.i(TAG, "Running periodic background geofence verification and sync...")
                val geofencedTasks = db.taskDao().getGeofencedTasks()
                Log.i(TAG, "Active geofenced tasks count in Room DB: ${geofencedTasks.size}")
                return Result.success()
            }

            // Extract Task ID from requestId (e.g. "task_42")
            val taskId = requestId.removePrefix("task_").toIntOrNull()
            val task = if (taskId != null) db.taskDao().getTaskById(taskId) else null

            if (task != null && !task.isDone && !task.isSoftDeleted) {
                val transitionLabel = when (transitionType) {
                    1 -> "ARRIVED AT"
                    2 -> "DEPARTED FROM"
                    else -> "NEAR"
                }

                val title = "📍 $transitionLabel ${task.locationName ?: task.safeTitle}"
                val message = "${task.safeTitle} • ${task.safeTriggerDirection} alert active!"

                // Log diagnostic dwell event to Room DB
                try {
                    db.geofenceEventLogDao().insertLog(
                        GeofenceEventLog(
                            geofenceId = "task_${task.id}",
                            taskTitle = task.safeTitle,
                            transitionType = transitionLabel,
                            latitude = lat,
                            longitude = lng,
                            accuracyMeters = 15f,
                            notes = "Triggered location: ${task.locationName ?: "Saved Location"}"
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to log geofence event: ${e.message}")
                }

                // Deliver High-Priority Notification with Ringtone & Vibration
                sendGeofenceNotification(context, task.id, title, message, task.priority)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in GeofenceBackgroundWorker: ${e.message}", e)
            Result.failure()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun sendGeofenceNotification(
        context: Context,
        taskId: Int,
        title: String,
        message: String,
        priority: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarms delivered when entering or exiting task location boundaries."
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TASK_ID", taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_DISMISS
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10 + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_SNOOZE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskActionReceiver.EXTRA_SNOOZE_MINUTES, 15)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.img_cobby_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .addAction(0, "✔ Dismiss", dismissPendingIntent)
            .addAction(0, "⏰ Snooze 15m", snoozePendingIntent)
            .build()

        notificationManager.notify(taskId + 10000, notification)

        // Trigger full-screen alarm activity for high-priority tasks
        try {
            AlarmRingtonePlayer.startRingtone(context)
            val fullScreenIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                putExtra(ReminderAlarmActivity.EXTRA_TASK_ID, taskId)
                putExtra(ReminderAlarmActivity.EXTRA_TASK_TITLE, title)
                putExtra(ReminderAlarmActivity.EXTRA_TASK_DESCRIPTION, message)
                putExtra(ReminderAlarmActivity.EXTRA_TASK_PRIORITY, priority)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch full-screen alarm activity: ${e.message}")
        }
    }
}
