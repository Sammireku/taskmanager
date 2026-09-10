package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.CobbyaiApp
import com.example.MainActivity
import com.example.R
import com.example.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskWidgetProvider : AppWidgetProvider() {

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_COMPLETE_TASK = "com.example.widget.ACTION_COMPLETE_TASK"
        const val EXTRA_TASK_ID = "com.example.widget.EXTRA_TASK_ID"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, TaskWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COMPLETE_TASK) {
            val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
            if (taskId != -1) {
                providerScope.launch {
                    val app = context.applicationContext as? CobbyaiApp
                    val db = app?.database
                    val task = db?.taskDao()?.getTaskById(taskId)
                    if (task != null) {
                        db.taskDao().updateTask(task.copy(status = "COMPLETED"))
                        withContext(Dispatchers.Main) {
                            updateAllWidgets(context)
                        }
                    }
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        providerScope.launch {
            val app = context.applicationContext as? CobbyaiApp
            val db = app?.database
            val tasks = try {
                db?.taskDao()?.getAllTasks()?.first() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            // Filter for pending, prioritising High priority then others
            val pendingTasks = tasks.filter { !it.isDone }
                .sortedWith(
                    compareByDescending<Task> { it.priority.equals("High", ignoreCase = true) }
                        .thenBy { it.dueDate ?: Long.MAX_VALUE }
                )

            for (appWidgetId in appWidgetIds) {
                val views = buildWidgetViews(context, appWidgetId, pendingTasks)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun buildWidgetViews(context: Context, appWidgetId: Int, pendingTasks: List<Task>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.task_widget_layout)

        // Title Intent -> open Main app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_header_container, openAppPendingIntent)

        // Add Task Intent -> open Create Task screen directly in MainActivity
        val addTaskIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_CREATE_TASK", true)
        }
        val addTaskPendingIntent = PendingIntent.getActivity(
            context,
            999,
            addTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add_btn, addTaskPendingIntent)

        val highCount = pendingTasks.count { it.priority.equals("High", ignoreCase = true) }
        views.setTextViewText(R.id.widget_badge, "${pendingTasks.size} pending" + if (highCount > 0) " • $highCount high" else "")

        // Refresh action
        val refreshIntent = Intent(context, TaskWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPendingIntent)

        // Populate slots
        val taskRowIds = listOf(R.id.widget_task_row_1, R.id.widget_task_row_2, R.id.widget_task_row_3)
        val taskTitleIds = listOf(R.id.widget_task_title_1, R.id.widget_task_title_2, R.id.widget_task_title_3)
        val taskSubIds = listOf(R.id.widget_task_sub_1, R.id.widget_task_sub_2, R.id.widget_task_sub_3)
        val taskCheckIds = listOf(R.id.widget_task_check_1, R.id.widget_task_check_2, R.id.widget_task_check_3)
        val taskPriorityBadgeIds = listOf(R.id.widget_task_priority_1, R.id.widget_task_priority_2, R.id.widget_task_priority_3)

        if (pendingTasks.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            taskRowIds.forEach { views.setViewVisibility(it, View.GONE) }
        } else {
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            for (i in 0 until 3) {
                if (i < pendingTasks.size) {
                    val task = pendingTasks[i]
                    views.setViewVisibility(taskRowIds[i], View.VISIBLE)
                    views.setTextViewText(taskTitleIds[i], task.title)

                    val subInfo = buildString {
                        if (!task.locationName.isNullOrBlank()) {
                            append("📍 ${task.locationName} ")
                        }
                        if (!task.category.isNullOrBlank()) {
                            append("• ${task.category}")
                        }
                    }
                    if (subInfo.isNotBlank()) {
                        views.setViewVisibility(taskSubIds[i], View.VISIBLE)
                        views.setTextViewText(taskSubIds[i], subInfo)
                    } else {
                        views.setViewVisibility(taskSubIds[i], View.GONE)
                    }

                    if (task.priority.equals("High", ignoreCase = true)) {
                        views.setViewVisibility(taskPriorityBadgeIds[i], View.VISIBLE)
                    } else {
                        views.setViewVisibility(taskPriorityBadgeIds[i], View.GONE)
                    }

                    // Deep link on clicking row -> opens task detail in app
                    val detailIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        data = Uri.parse("cobbyai://task/${task.id}")
                        putExtra("taskId", task.id)
                    }
                    val detailPendingIntent = PendingIntent.getActivity(
                        context,
                        task.id + 1000,
                        detailIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(taskRowIds[i], detailPendingIntent)

                    // Quick complete button intent
                    val completeIntent = Intent(context, TaskWidgetProvider::class.java).apply {
                        action = ACTION_COMPLETE_TASK
                        putExtra(EXTRA_TASK_ID, task.id)
                    }
                    val completePendingIntent = PendingIntent.getBroadcast(
                        context,
                        task.id + 5000,
                        completeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(taskCheckIds[i], completePendingIntent)
                } else {
                    views.setViewVisibility(taskRowIds[i], View.GONE)
                }
            }
        }

        return views
    }
}
