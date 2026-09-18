package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * BroadcastReceiver invoked by AlarmManager when a scheduled task's due date arrives.
 * Delivers distinct high-priority notifications with sound and rich details.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskAlarmReceiver"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESCRIPTION = "extra_task_description"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_CATEGORY = "extra_task_category"
        const val EXTRA_TASK_REMINDER_TONE = "extra_task_reminder_tone"

        const val HIGH_PRIORITY_CHANNEL_ID = "cobbyai_high_priority_tasks_v3"
        const val HIGH_PRIORITY_CHANNEL_NAME = "High Priority Task Alerts"

        const val GENERAL_TASKS_CHANNEL_ID = "cobbyai_general_tasks_v3"
        const val GENERAL_TASKS_CHANNEL_NAME = "General Task Reminders"

        /**
         * Ensures notification channels are initialized with sound and vibration enabled.
         */
        fun initializeNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val alarmSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val alarmAudioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                val notificationAudioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val highPriorityChannel = NotificationChannel(
                    HIGH_PRIORITY_CHANNEL_ID,
                    HIGH_PRIORITY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    this.description = "Urgent and high-priority task alerts with sound"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    enableLights(true)
                    setShowBadge(true)
                    setSound(alarmSoundUri, alarmAudioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(highPriorityChannel)

                val generalChannel = NotificationChannel(
                    GENERAL_TASKS_CHANNEL_ID,
                    GENERAL_TASKS_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    this.description = "Standard task reminder notifications with sound"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    enableLights(true)
                    setShowBadge(true)
                    setSound(alarmSoundUri, notificationAudioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(generalChannel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "Cobbyai:TaskAlarmReceiverWakeLock"
        )
        try {
            wakeLock.acquire(60000L) // Hold CPU awake during critical alarm sequence

            val taskId = intent.getIntExtra(EXTRA_TASK_ID, 0)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Due"
        val description = intent.getStringExtra(EXTRA_TASK_DESCRIPTION).orEmpty()
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "Medium"
        val category = intent.getStringExtra(EXTRA_TASK_CATEGORY) ?: "General"
        val reminderTone = intent.getStringExtra(EXTRA_TASK_REMINDER_TONE) ?: "DEFAULT"

        Log.d(TAG, "Alarm triggered for task #$taskId: '$title', priority: $priority, tone: $reminderTone")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        initializeNotificationChannels(context)

        val isHighPriority = priority.equals("High", ignoreCase = true)
        val targetChannelId = if (isHighPriority) HIGH_PRIORITY_CHANNEL_ID else GENERAL_TASKS_CHANNEL_ID

        val soundUri: Uri = when (reminderTone) {
            "URGENT_ALARM" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "GENTLE_NOTIF" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            "PHONE_RINGTONE" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> if (isHighPriority) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        }

        val prefs = com.example.data.PreferencesManager(context)

        // Start active ringtone & vibration loop only if sound feedback is enabled
        if (prefs.isSoundFeedbackEnabled) {
            AlarmRingtonePlayer.startRingtone(context, soundUri)
        }

        // Full Screen Intent pointing to ReminderAlarmActivity
        val fullScreenIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra(ReminderAlarmActivity.EXTRA_TASK_ID, taskId)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_TITLE, title)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_DESCRIPTION, description)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_PRIORITY, priority)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_CATEGORY, category)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            taskId + 2000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Snooze 10 Minutes
        val snoozeIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_SNOOZE
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId + 3000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Reschedule
        val rescheduleIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra(ReminderAlarmActivity.EXTRA_TASK_ID, taskId)
            putExtra(ReminderAlarmActivity.EXTRA_TASK_TITLE, title)
            putExtra(ReminderAlarmActivity.EXTRA_OPEN_RESCHEDULE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val reschedulePendingIntent = PendingIntent.getActivity(
            context,
            taskId + 4000,
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Close / Dismiss
        val dismissIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_DISMISS
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            taskId + 5000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap action: open task details in MainActivity via deep link
        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("cobbyai://task?id=$taskId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = if (isHighPriority) {
            "🚨 Urgent: $title"
        } else {
            "⏰ Due Now: $title"
        }

        val contentBody = when {
            description.isNotBlank() -> description
            category.isNotBlank() && category != "General" -> "Category: $category"
            else -> "Your scheduled task is due now."
        }

        val notifPriority = when {
            !prefs.isBannerNotificationsEnabled -> NotificationCompat.PRIORITY_DEFAULT
            isHighPriority -> NotificationCompat.PRIORITY_MAX
            else -> NotificationCompat.PRIORITY_HIGH
        }

        val notificationBuilder = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notificationTitle)
            .setContentText(contentBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(if (description.isNotBlank()) "$contentBody\n[Category: $category | Priority: $priority]" else contentBody)
            )
            .setPriority(notifPriority)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Snooze 10m", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_today, "Reschedule", reschedulePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", dismissPendingIntent)

        if (prefs.isSoundFeedbackEnabled) {
            notificationBuilder.setSound(soundUri)
            notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500))
        } else {
            notificationBuilder.setSound(null)
            notificationBuilder.setVibrate(longArrayOf(0))
        }

        if (prefs.isFullscreenAlarmEnabled) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        if (isHighPriority) {
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_ALARM)
        } else {
            notificationBuilder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }

        notificationManager.notify(taskId, notificationBuilder.build())

        // Launch full screen activity directly if enabled
        if (prefs.isFullscreenAlarmEnabled) {
            try {
                context.startActivity(fullScreenIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start full-screen activity directly: ${e.message}")
            }
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error in TaskAlarmReceiver.onReceive: ${e.message}", e)
        } finally {
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wakeLock: ${e.message}")
            }
        }
    }
}
