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
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase

class HabitReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "HabitReminderWorker"
        const val CHANNEL_ID = "cobbyai_habit_reminders_work"
        const val CHANNEL_NAME = "Daily Habit Reminders"

        const val KEY_HABIT_ID = "key_habit_id"
        const val KEY_HABIT_TITLE = "key_habit_title"
    }

    override suspend fun doWork(): Result {
        return try {
            val customTitle = inputData.getString(KEY_HABIT_TITLE)

            if (!customTitle.isNullOrBlank()) {
                sendHabitNotification(
                    title = "🔄 Habit Reminder: $customTitle",
                    body = "Time to complete your daily habit and maintain your streak!"
                )
                return Result.success()
            }

            // Query pending habits from Room DB
            val db = AppDatabase.getInstance(applicationContext)

            val allTasks = db.taskDao().getAllTasksList()
            val pendingHabits = allTasks.filter { it.isHabit && !it.isDone && !it.isSoftDeleted }

            if (pendingHabits.isNotEmpty()) {
                val habitListStr = pendingHabits.take(3).joinToString(", ") { it.safeTitle }
                val countText = if (pendingHabits.size == 1) "1 active habit" else "${pendingHabits.size} active habits"
                sendHabitNotification(
                    title = "🔄 Daily Habit Reminder",
                    body = "You have $countText pending today ($habitListStr). Keep your streak going!"
                )
            } else {
                Log.d(TAG, "No pending habits for today; skipping notification.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing HabitReminderWorker", e)
            Result.failure()
        }
    }

    private fun sendHabitNotification(title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to maintain active habits and streaks"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            .build()

        notificationManager.notify(9001, notification)
    }
}
