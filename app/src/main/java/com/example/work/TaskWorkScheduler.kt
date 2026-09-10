package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.Task
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduler that registers background triggers for upcoming task due dates
 * and daily habit reminders.
 */
class TaskWorkScheduler(private val context: Context) {

    companion object {
        private const val TAG = "TaskWorkScheduler"
        private const val UNIQUE_WORK_PREFIX = "task_due_work_"
        private const val DAILY_HABIT_WORK_NAME = "daily_habit_reminders_work"

        fun getWorkNameForTask(taskId: Int): String = "$UNIQUE_WORK_PREFIX$taskId"
    }

    private val workManager: WorkManager?
        get() = try {
            WorkManager.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager instance unavailable", e)
            null
        }

    /**
     * Schedules a WorkManager trigger for a task's due date.
     * Calculates delay from now to dueDate. If dueDate is in the past or task is completed,
     * cancels any pending work.
     */
    fun scheduleDueDateReminder(task: Task) {
        val dueDate = task.dueDate
        if (dueDate == null || task.isDone) {
            cancelDueDateReminder(task.id)
            return
        }

        val currentTime = System.currentTimeMillis()
        val delayMs = dueDate - currentTime

        if (delayMs <= 0) {
            Log.d(TAG, "Task #${task.id} due date is already in the past; skipping future trigger.")
            cancelDueDateReminder(task.id)
            return
        }

        val inputData = Data.Builder()
            .putInt(TaskReminderWorker.KEY_TASK_ID, task.id)
            .putString(TaskReminderWorker.KEY_TASK_TITLE, task.safeTitle)
            .putString(TaskReminderWorker.KEY_TASK_DESCRIPTION, task.description.orEmpty())
            .putString(TaskReminderWorker.KEY_TASK_PRIORITY, task.safePriority)
            .putLong(TaskReminderWorker.KEY_DUE_DATE, dueDate)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("task_${task.id}")
            .addTag("task_due_notifications")
            .build()

        val uniqueWorkName = getWorkNameForTask(task.id)
        workManager?.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "Scheduled WorkManager due date trigger for task #${task.id} in ${delayMs / 1000}s ($uniqueWorkName)")
    }

    /**
     * Cancels any scheduled WorkManager trigger for the specified task id.
     */
    fun cancelDueDateReminder(taskId: Int) {
        val uniqueWorkName = getWorkNameForTask(taskId)
        workManager?.cancelUniqueWork(uniqueWorkName)
        Log.d(TAG, "Cancelled WorkManager trigger for task #$taskId ($uniqueWorkName)")
    }

    /**
     * Schedules daily habit reminders using WorkManager to periodically notify the user
     * about active daily habits.
     */
    fun scheduleDailyHabitReminders() {
        val habitWorkRequest = PeriodicWorkRequestBuilder<HabitReminderWorker>(
            24, TimeUnit.HOURS
        )
            .addTag("daily_habit_reminders")
            .build()

        workManager?.enqueueUniquePeriodicWork(
            DAILY_HABIT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            habitWorkRequest
        )
        Log.d(TAG, "Enqueued periodic daily habit reminder WorkManager task.")
    }

    /**
     * Immediate trigger to test daily habit reminder WorkManager notification pipeline.
     */
    fun triggerImmediateHabitReminderWork(habitTitle: String = "Daily Mindfulness & Planning") {
        val inputData = Data.Builder()
            .putString(HabitReminderWorker.KEY_HABIT_TITLE, habitTitle)
            .build()

        val testWorkRequest = OneTimeWorkRequestBuilder<HabitReminderWorker>()
            .setInputData(inputData)
            .addTag("test_habit_notification")
            .build()

        workManager?.enqueue(testWorkRequest)
        Log.d(TAG, "Enqueued immediate test habit reminder notification for $habitTitle")
    }

    /**
     * Immediate trigger to test the WorkManager push notification pipeline.
     */
    fun triggerImmediateTestWork(task: Task) {
        val inputData = Data.Builder()
            .putInt(TaskReminderWorker.KEY_TASK_ID, task.id)
            .putString(TaskReminderWorker.KEY_TASK_TITLE, task.safeTitle)
            .putString(TaskReminderWorker.KEY_TASK_DESCRIPTION, task.description ?: "Testing WorkManager local push notifications!")
            .putString(TaskReminderWorker.KEY_TASK_PRIORITY, task.safePriority)
            .putLong(TaskReminderWorker.KEY_DUE_DATE, System.currentTimeMillis())
            .build()

        val testWorkRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInputData(inputData)
            .addTag("test_work_notification")
            .build()

        workManager?.enqueue(testWorkRequest)
        Log.d(TAG, "Enqueued immediate test WorkManager notification for task #${task.id}")
    }

    /**
     * Schedules periodic background purging of soft-deleted tasks older than 30 days.
     */
    fun schedulePeriodicTrashPurge() {
        val purgeRequest = androidx.work.PeriodicWorkRequestBuilder<TrashPurgeWorker>(
            24, TimeUnit.HOURS
        ).addTag("trash_purge_job").build()

        workManager?.enqueueUniquePeriodicWork(
            "trash_30day_purge_work",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest
        )
        Log.d(TAG, "Enqueued periodic 24h background trash purge work")
    }

    /**
     * Schedules periodic background cloud sync constrained to connected network and healthy battery.
     */
    fun schedulePeriodicCloudSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<CloudSyncWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag("periodic_cloud_sync")
            .build()

        workManager?.enqueueUniquePeriodicWork(
            CloudSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.d(TAG, "Enqueued periodic cloud sync with NetworkType.CONNECTED constraints.")
    }

    /**
     * Enqueues an immediate cloud sync constrained to connected network.
     */
    fun enqueueImmediateCloudSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .addTag("immediate_cloud_sync")
            .build()

        workManager?.enqueueUniqueWork(
            "immediate_sync_run",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Log.d(TAG, "Enqueued immediate cloud sync with NetworkType.CONNECTED constraint.")
    }

    /**
     * Schedules periodic geofence radius calibration based on GPS accuracy signals.
     */
    fun schedulePeriodicGeofenceCalibration() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val calRequest = androidx.work.PeriodicWorkRequestBuilder<GeofenceCalibrationWorker>(
            2, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag("geofence_calibration")
            .build()

        workManager?.enqueueUniquePeriodicWork(
            GeofenceCalibrationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            calRequest
        )
        Log.d(TAG, "Enqueued periodic geofence calibration job with battery constraints.")
    }

    /**
     * Immediately runs a geofence calibration pass to adjust radii against current GPS accuracy.
     */
    fun enqueueImmediateCalibration() {
        val calRequest = OneTimeWorkRequestBuilder<GeofenceCalibrationWorker>()
            .addTag("immediate_geofence_calibration")
            .build()

        workManager?.enqueue(calRequest)
        Log.d(TAG, "Enqueued immediate geofence calibration pass.")
    }
}
