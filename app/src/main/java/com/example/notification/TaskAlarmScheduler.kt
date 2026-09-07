package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Task

/**
 * Manages exact and inexact task alarms via Android's AlarmManager,
 * ensuring timely notifications for tasks (especially high-priority items)
 * when their scheduled due date arrives.
 */
class TaskAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "TaskAlarmScheduler"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules an alarm for the given task based on its dueDate.
     * If the task has no due date, is in the past, or is completed, cancels any existing alarm.
     */
    fun scheduleTaskAlarm(task: Task) {
        val dueDate = task.dueDate
        if (dueDate == null || dueDate <= System.currentTimeMillis() || task.isDone) {
            cancelTaskAlarm(task.id)
            return
        }

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.safeTitle)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_DESCRIPTION, task.description.orEmpty())
            putExtra(TaskAlarmReceiver.EXTRA_TASK_PRIORITY, task.safePriority)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_CATEGORY, task.safeCategory)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_REMINDER_TONE, task.safeReminderTone)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        dueDate,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for task #${task.id} at $dueDate (${task.safePriority})")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        dueDate,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled inexact alarm for task #${task.id} at $dueDate (${task.safePriority})")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    dueDate,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for task #${task.id} at $dueDate")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission not granted, falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueDate,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for task #${task.id}", e)
        }
    }

    /**
     * Cancels any scheduled alarm for the task with the given ID.
     */
    fun cancelTaskAlarm(taskId: Int) {
        try {
            val intent = Intent(context, TaskAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "Cancelled alarm for task #$taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling alarm for task #$taskId", e)
        }
    }
}
