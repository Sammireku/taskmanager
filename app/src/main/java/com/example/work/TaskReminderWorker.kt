package com.example.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R

class TaskReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "TaskReminderWorker"
        const val CHANNEL_ID = "cobbyai_task_reminders_work"
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

        sendNotification(taskId, title, description, priority)
        return Result.success()
    }

    private fun sendNotification(
        taskId: Int,
        title: String,
        description: String,
        priority: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "WorkManager push notifications for task deadlines"
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(channel)
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
            "high" -> "🔥 HIGH PRIORITY"
            "low" -> "🟢 Low Priority"
            else -> "⚡ Medium Priority"
        }

        val contentText = if (description.isNotBlank()) {
            "[$priorityBadge] $description"
        } else {
            "[$priorityBadge] This task is due now. Open Cobby to complete it!"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ Due: $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\n\nScheduled via WorkManager trigger."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            .build()

        val notificationId = if (taskId > 0) taskId else (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, notification)
    }
}
