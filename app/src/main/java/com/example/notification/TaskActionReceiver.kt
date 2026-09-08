package com.example.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.CobbyaiApp
import com.example.widget.TaskWidgetProvider
import com.example.work.TaskWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.example.notification.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.example.notification.ACTION_DISMISS"
        const val ACTION_RESCHEDULE = "com.example.notification.ACTION_RESCHEDULE"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        val action = intent.action ?: return

        AlarmRingtonePlayer.stopRingtone()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (taskId != -1) {
            notificationManager.cancel(taskId)
        }

        when (action) {
            ACTION_SNOOZE -> {
                val snoozeMins = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                if (taskId != -1) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val app = context.applicationContext as? CobbyaiApp
                            val db = app?.database
                            val task = db?.taskDao()?.getTaskById(taskId)
                            if (task != null) {
                                val newDueDate = System.currentTimeMillis() + (snoozeMins * 60 * 1000L)
                                val updatedTask = task.copy(dueDate = newDueDate)
                                db.taskDao().updateTask(updatedTask)

                                TaskWorkScheduler(context).scheduleDueDateReminder(updatedTask)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "⏰ Task snoozed for $snoozeMins minutes",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    TaskWidgetProvider.updateAllWidgets(context)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    Toast.makeText(context, "Snoozed for $snoozeMins minutes", Toast.LENGTH_SHORT).show()
                }
            }

            ACTION_DISMISS -> {
                Toast.makeText(context, "Reminder dismissed", Toast.LENGTH_SHORT).show()
            }

            ACTION_RESCHEDULE -> {
                val rescheduleIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                    putExtra(ReminderAlarmActivity.EXTRA_TASK_ID, taskId)
                    putExtra(ReminderAlarmActivity.EXTRA_OPEN_RESCHEDULE, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(rescheduleIntent)
            }
        }
    }
}
