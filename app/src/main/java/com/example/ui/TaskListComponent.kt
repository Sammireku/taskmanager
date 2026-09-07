package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A reusable Compose UI component that displays a list of tasks using a LazyColumn,
 * showing the title and due date for each item with Material 3 styling.
 */
@Composable
fun TaskList(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    onToggleComplete: (Task) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    distanceProvider: ((Task) -> String?)? = null,
    subtasksCountProvider: ((Task) -> String?)? = null,
    emptyContent: (@Composable () -> Unit)? = null
) {
    if (tasks.isEmpty() && emptyContent != null) {
        emptyContent()
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .testTag("task_lazy_column"),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = tasks,
                key = { it.id }
            ) { task ->
                TaskListItem(
                    task = task,
                    onClick = { onTaskClick(task) },
                    onToggleComplete = { onToggleComplete(task) },
                    distanceText = distanceProvider?.invoke(task),
                    subtasksCount = subtasksCountProvider?.invoke(task),
                    modifier = Modifier.animateItem(
                        fadeOutSpec = spring()
                    )
                )
            }
        }
    }
}

/**
 * An individual item in the task list prominently displaying the task title,
 * formatted due date, priority chip, category, and completion toggle.
 */
@Composable
fun TaskListItem(
    task: Task,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
    distanceText: String? = null,
    subtasksCount: String? = null
) {
    val isDone = task.isDone
    val formattedDueDate = remember(task.dueDate) {
        task.dueDate?.let { formatTaskDueDate(it) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Completion Toggle Checkbox
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("task_checkbox_${task.id}")
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isDone) "Mark ${task.safeTitle} as incomplete" else "Mark ${task.safeTitle} as complete",
                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Task Title
                Text(
                    text = task.safeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.SemiBold,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("task_title_${task.id}")
                )

                // Optional Description
                if (!task.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Metadata Row: Due Date, Priority, Category, Location, Subtasks
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Due Date Display (Prominent)
                    if (formattedDueDate != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("task_due_date_${task.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Due date",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formattedDueDate,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Unset due date indicator
                        Text(
                            text = "No due date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                            modifier = Modifier.testTag("task_due_date_none_${task.id}")
                        )
                    }

                    // Priority Badge
                    val priorityColor = when (task.safePriority.lowercase(Locale.ROOT)) {
                        "high" -> MaterialTheme.colorScheme.error
                        "low" -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    Surface(
                        color = priorityColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = task.safePriority,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Category Pill
                    if (!task.category.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = task.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Subtasks count badge
                    if (subtasksCount != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "☑ $subtasksCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Location indicator
                    if (distanceText != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                val isDeparture = task.safeTriggerDirection.equals("DEPARTURE", ignoreCase = true)
                                Icon(
                                    imageVector = if (isDeparture) Icons.Default.FlightTakeoff else Icons.Default.FlightLand,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = distanceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else if (!task.locationName.isNullOrBlank() || task.latitude != null) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location: ${task.locationName ?: ""}",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a due date timestamp into a human-friendly string e.g. "Today, 3:00 PM"
 * or "Tomorrow, 9:00 AM" or "Oct 12, 2:00 PM".
 */
fun formatTaskDueDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val dueCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val sameYear = now.get(Calendar.YEAR) == dueCal.get(Calendar.YEAR)
    val dayOfYearDiff = dueCal.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timePart = timeFormat.format(Date(timestamp))

    return when {
        sameYear && dayOfYearDiff == 0 -> "Today, $timePart"
        sameYear && dayOfYearDiff == 1 -> "Tomorrow, $timePart"
        sameYear && dayOfYearDiff == -1 -> "Yesterday, $timePart"
        sameYear -> {
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
