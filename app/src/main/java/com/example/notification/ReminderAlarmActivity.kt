package com.example.notification

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CobbyaiApp
import com.example.MainActivity
import com.example.R
import com.example.data.Task
import com.example.ui.theme.CobbyaiTheme
import com.example.widget.TaskWidgetProvider
import com.example.work.TaskWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderAlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESCRIPTION = "extra_task_description"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_CATEGORY = "extra_task_category"
        const val EXTRA_OPEN_RESCHEDULE = "extra_open_reschedule"
    }

    private var taskId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen up & display over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        val initialTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Scheduled Task Due"
        val initialDesc = intent.getStringExtra(EXTRA_TASK_DESCRIPTION).orEmpty()
        val initialPriority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "Medium"
        val initialCategory = intent.getStringExtra(EXTRA_TASK_CATEGORY) ?: "General"
        val openReschedule = intent.getBooleanExtra(EXTRA_OPEN_RESCHEDULE, false)

        // Start ringing if task alert
        AlarmRingtonePlayer.startRingtone(this)

        enableEdgeToEdge()
        setContent {
            CobbyaiTheme {
                ReminderAlarmContent(
                    taskId = taskId,
                    initialTitle = initialTitle,
                    initialDesc = initialDesc,
                    initialPriority = initialPriority,
                    initialCategory = initialCategory,
                    openRescheduleOnStart = openReschedule,
                    onSnooze = { minutes -> snoozeTask(minutes) },
                    onReschedulePicked = { newTimestamp -> rescheduleTask(newTimestamp) },
                    onDismiss = { dismissAlarm() },
                    onOpenApp = {
                        dismissAlarm()
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("taskId", taskId)
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmRingtonePlayer.stopRingtone()
    }

    private fun snoozeTask(minutes: Int) {
        AlarmRingtonePlayer.stopRingtone()
        cancelNotification()

        if (taskId != -1) {
            val app = applicationContext as? CobbyaiApp
            val db = app?.database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = db?.taskDao()?.getTaskById(taskId)
                    if (task != null) {
                        val newTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        val updated = task.copy(dueDate = newTime)
                        db.taskDao().updateTask(updated)
                        TaskWorkScheduler(applicationContext).scheduleDueDateReminder(updated)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ReminderAlarmActivity,
                                "⏰ Snoozed for $minutes minutes",
                                Toast.LENGTH_SHORT
                            ).show()
                            TaskWidgetProvider.updateAllWidgets(applicationContext)
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) { finish() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { finish() }
                }
            }
        } else {
            Toast.makeText(this, "Snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun rescheduleTask(newTimestamp: Long) {
        AlarmRingtonePlayer.stopRingtone()
        cancelNotification()

        if (taskId != -1) {
            val app = applicationContext as? CobbyaiApp
            val db = app?.database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = db?.taskDao()?.getTaskById(taskId)
                    if (task != null) {
                        val updated = task.copy(dueDate = newTimestamp)
                        db.taskDao().updateTask(updated)
                        TaskWorkScheduler(applicationContext).scheduleDueDateReminder(updated)

                        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        val formatted = sdf.format(Date(newTimestamp))

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@ReminderAlarmActivity,
                                "📅 Rescheduled to $formatted",
                                Toast.LENGTH_SHORT
                            ).show()
                            TaskWidgetProvider.updateAllWidgets(applicationContext)
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) { finish() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { finish() }
                }
            }
        } else {
            finish()
        }
    }

    private fun dismissAlarm() {
        AlarmRingtonePlayer.stopRingtone()
        cancelNotification()
        Toast.makeText(this, "Reminder dismissed", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun cancelNotification() {
        if (taskId != -1) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId)
        }
    }
}

// Coroutine Scope helper inside activity
private class CoroutineScope(override val coroutineContext: kotlin.coroutines.CoroutineContext) : kotlinx.coroutines.CoroutineScope

@Composable
fun ReminderAlarmContent(
    taskId: Int,
    initialTitle: String,
    initialDesc: String,
    initialPriority: String,
    initialCategory: String,
    openRescheduleOnStart: Boolean,
    onSnooze: (Int) -> Unit,
    onReschedulePicked: (Long) -> Unit,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedSnoozeMins by remember { mutableIntStateOf(10) }
    val isHighPriority = initialPriority.equals("High", ignoreCase = true)

    // Pulsing animation for alarm bell
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    var showDateTimePicker by remember { mutableStateOf(openRescheduleOnStart) }

    if (showDateTimePicker) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)

                        onReschedulePicked(calendar.timeInMillis)
                        showDateTimePicker = false
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showDateTimePicker = false }
            show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Pulse Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (isHighPriority) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.primaryContainer
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isHighPriority) Color(0xFFBA1A1A) else MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Ringing Alarm",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isHighPriority) "🚨 URGENT TASK ALERT" else "⏰ TASK REMINDER",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighPriority) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = initialTitle,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (initialDesc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = initialDesc,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHighPriority) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isHighPriority) "🔥 HIGH PRIORITY" else "⚡ $initialPriority Priority",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHighPriority) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (initialCategory.isNotBlank() && initialCategory != "General") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "🏷️ $initialCategory",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Middle Section: Snooze & Reschedule Banner Actions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Snooze Reminder",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(5, 10, 15, 30, 60).forEach { mins ->
                            val isSelected = selectedSnoozeMins == mins
                            AssistChip(
                                onClick = { selectedSnoozeMins = mins },
                                label = {
                                    Text(
                                        text = if (mins == 60) "1h" else "${mins}m",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else AssistChipDefaults.assistChipBorder(enabled = true)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onSnooze(selectedSnoozeMins) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Snooze, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Snooze for $selectedSnoozeMins Minutes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showDateTimePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reschedule Date & Time",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Bottom Section: Close & View Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Close / Dismiss Alarm",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "View Task Details in App", fontSize = 14.sp)
                }
            }
        }
    }
}

private fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)
