package com.example.work

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.Task
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduler that registers background triggers for upcoming task due dates.
 * Delivers local push notifications reliably across system idle and reboots.
 */
class TaskWorkScheduler(private val context: Context) {

    companion object {
        private const val TAG = "TaskWorkScheduler"
        private const val UNIQUE_WORK_PREFIX = "task_due_work_"

        fun getWorkNameForTask(taskId: Int): String = "$UNIQUE_WORK_PREFIX$taskId"
    }

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

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
        workManager.enqueueUniqueWork(
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
        workManager.cancelUniqueWork(uniqueWorkName)
        Log.d(TAG, "Cancelled WorkManager trigger for task #$taskId ($uniqueWorkName)")
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

        workManager.enqueue(testWorkRequest)
        Log.d(TAG, "Enqueued immediate test WorkManager notification for task #${task.id}")
    }

    /**
     * Schedules periodic background purging of soft-deleted tasks older than 30 days.
     */
    fun schedulePeriodicTrashPurge() {
        val purgeRequest = androidx.work.PeriodicWorkRequestBuilder<TrashPurgeWorker>(
            24, TimeUnit.HOURS
        ).addTag("trash_purge_job").build()

        workManager.enqueueUniquePeriodicWork(
            "trash_30day_purge_work",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest
        )
        Log.d(TAG, "Enqueued periodic 24h background trash purge work")
    }
}
