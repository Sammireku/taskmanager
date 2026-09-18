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
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.notification.AlarmRingtonePlayer
import com.example.notification.ReminderAlarmActivity
import com.example.notification.TaskActionReceiver

class TaskReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "TaskReminderWorker"
        const val CHANNEL_ID = "cobbyai_task_reminders_work_v2"
        const val CHANNEL_NAME = "Upcoming Task Due Dates"

        const val KEY_TASK_ID = "key_task_id"
        const val KEY_TASK_TITLE = "key_task_title"
        const val KEY_TASK_DESCRIPTION = "key_task_description"
        const val KEY_TASK_PRIORITY = "key_task_priority"
        const val KEY_DUE_DATE = "key_due_date"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getInt(KEY_TASK_ID, -1)
        val title = inputData.getString(KEY_TASK_TITLE) ?: "Upcoming Task"
        val description = inputData.getString(KEY_TASK_DESCRIPTION).orEmpty()
        val priority = inputData.getString(KEY_TASK_PRIORITY) ?: "Medium"

        Log.d(TAG, "Executing WorkManager reminder for task #$taskId: $title (Priority: $priority)")

        // Acquire CPU PowerManager.WakeLock during the critical alarm firing sequence
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Cobbyai:TaskReminderWorkerWakeLock"
        )

        try {
            wakeLock.acquire(60000L) // 60-second safety timeout for CPU processing power

            // Trigger alarm ringtone & vibration loop
            val alarmSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            AlarmRingtonePlayer.startRingtone(context, alarmSoundUri)

            sendNotification(taskId, title, description, priority, alarmSoundUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error firing task reminder alarm in WorkManager", e)
        } finally {
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                    Log.d(TAG, "Released WorkManager CPU WakeLock.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock: ${e.message}")
            }
        }

        return Result.success()
    }

    private fun sendNotification(
        taskId: Int,
        title: String,
        description: String,
        priority: String,
        alarmSoundUri: Uri
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Urgent task alarm notifications with full screen alert support"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(alarmSoundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 1. Full Screen Intent pointing to ReminderAlarmActivity
        val fullScreenIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra(ReminderAlarmActivity.EXTRA_TASK_ID, taskId)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_TITLE, title)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_DESCRIPTION, description)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_PRIORITY, priority)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            taskId.coerceAtLeast(0) + 5000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Directly launch ReminderAlarmActivity as well
        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start ReminderAlarmActivity directly: ${e.message}")
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO_TASK_ID", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.coerceAtLeast(0),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priorityBadge = when (priority.lowercase()) {
            "high" -> "🚨 HIGH PRIORITY"
            "low" -> "🟢 Low Priority"
            else -> "⚡ Medium Priority"
        }

        val contentText = if (description.isNotBlank()) {
            "[$priorityBadge] $description"
        } else {
            "[$priorityBadge] Task is due now!"
        }

        val dismissIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_DISMISS
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.coerceAtLeast(0) + 6000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ Due: $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n\nScheduled task alert."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(alarmSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Alarm", dismissPendingIntent)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            .build()

        val notificationId = if (taskId > 0) taskId else (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, notification)
    }
}
