package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.Task
import com.example.gemini.GeminiTaskHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(
    taskId: Int? = null,
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allTasks by viewModel.allTasks.collectAsState()
    val existingTask = remember(taskId, allTasks) {
        if (taskId != null) allTasks.find { it.id == taskId } else null
    }

    var title by remember(existingTask) { mutableStateOf(existingTask?.title ?: "") }
    var description by remember(existingTask) { mutableStateOf(existingTask?.description ?: "") }
    var priority by remember(existingTask) { mutableStateOf(existingTask?.priority ?: "Medium") }
    var status by remember(existingTask) { mutableStateOf(existingTask?.status ?: "PENDING") }
    var isHabit by remember(existingTask) { mutableStateOf(existingTask?.isHabit ?: false) }
    var habitFrequency by remember(existingTask) { mutableStateOf(existingTask?.habitFrequency ?: "Daily") }
    var category by remember(existingTask) { mutableStateOf(existingTask?.category ?: "General") }
    var reminderTone by remember(existingTask) { mutableStateOf(existingTask?.safeReminderTone ?: "DEFAULT") }
    var customCategoryInput by remember { mutableStateOf("") }
    // Voice Speech-to-Text integration
    var pendingVoiceField by remember { mutableStateOf<String?>(null) } // "TITLE", "DESC"

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (pendingVoiceField == "DESC") {
                    description = spoken
                } else {
                    title = spoken
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak task details...")
            }
            try {
                voiceLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Voice Recognition unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVoiceInput(field: String) {
        pendingVoiceField = field
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak task details...")
            }
            try {
                voiceLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Voice Recognition unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var dueDateMs by remember(existingTask) { mutableStateOf(existingTask?.dueDate) }

    // Google Maps Location & Geofence state
    var locationName by remember(existingTask) { mutableStateOf(existingTask?.locationName ?: "") }
    var latitude by remember(existingTask) { mutableStateOf(existingTask?.latitude) }
    var longitude by remember(existingTask) { mutableStateOf(existingTask?.longitude) }
    var geofenceRadius by remember(existingTask) { mutableStateOf(existingTask?.geofenceRadius ?: 250f) }
    var triggerDirection by remember(existingTask) { mutableStateOf(existingTask?.triggerDirection ?: "ARRIVAL") }
    var isUserSelectedTriggerDirection by remember { mutableStateOf(existingTask != null) }
    var locationSearchQuery by remember(existingTask) { mutableStateOf(existingTask?.locationName ?: "") }
    var isFetchingCurrentLocation by remember { mutableStateOf(false) }

    var showAddFrequentDialog by remember { mutableStateOf(false) }
    var newFrequentName by remember { mutableStateOf("") }
    var newFrequentAddress by remember { mutableStateOf("") }
    var newFrequentCategory by remember { mutableStateOf("CUSTOM") }

    // Dynamic Reactive Direction Trigger Auto-Detection (Only if user hasn't manually chosen)
    LaunchedEffect(title, description, locationSearchQuery) {
        if (!isUserSelectedTriggerDirection) {
            val detected = GeminiTaskHelper.detectTriggerDirection("$title $description $locationSearchQuery")
            if (detected == "DEPARTURE") {
                triggerDirection = "DEPARTURE"
            }
        }
    }

    val placeSuggestions by viewModel.placeSuggestions.collectAsState()
    val isSearchingPlaces by viewModel.isSearchingPlaces.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            viewModel.refreshLocation()
        }
    }

    var existingCommitments by remember { mutableStateOf("") }
    val isSuggestingSchedule by viewModel.isSuggestingSchedule.collectAsState()
    val scheduleSuggestion by viewModel.scheduleSuggestionState.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val isEditing = existingTask != null

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Task" else "Create Task",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("form_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val taskToSave = (existingTask ?: Task(title = title.trim())).copy(
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    priority = priority,
                                    status = status,
                                    isHabit = isHabit,
                                    habitFrequency = if (isHabit) habitFrequency else null,
                                    category = category,
                                    dueDate = dueDateMs,
                                    locationName = locationName.trim().ifBlank { locationSearchQuery.trim() }.ifBlank { null },
                                    latitude = latitude,
                                    longitude = longitude,
                                    geofenceRadius = geofenceRadius,
                                    triggerDirection = triggerDirection,
                                    reminderTone = reminderTone
                                )
                                if (isEditing) {
                                    viewModel.updateTask(taskToSave)
                                } else {
                                    viewModel.addTask(taskToSave)
                                }
                                onBack()
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_task_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Task Title Input
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g., Prepare Quarterly Presentation") },
                    trailingIcon = {
                        IconButton(
                            onClick = { launchVoiceInput("TITLE") },
                            modifier = Modifier.testTag("task_title_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input for title",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Description Input
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Add additional details or notes...") },
                    trailingIcon = {
                        IconButton(
                            onClick = { launchVoiceInput("DESC") },
                            modifier = Modifier.testTag("task_desc_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input for description",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_description_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Priority Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Priority Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("High", Color(0xFFD32F2F), Icons.Default.KeyboardDoubleArrowUp),
                                Triple("Medium", Color(0xFFE65100), Icons.Default.KeyboardArrowUp),
                                Triple("Low", Color(0xFF2E7D32), Icons.Default.KeyboardArrowDown)
                            ).forEach { (p, color, icon) ->
                                val selected = priority.equals(p, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { priority = p },
                                    label = { Text(p, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "$p Priority",
                                            tint = if (selected) color else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("priority_chip_${p.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = when (p.lowercase()) {
                                            "high" -> Color(0xFFFFEBEE)
                                            "medium" -> Color(0xFFFFF3E0)
                                            else -> Color(0xFFE8F5E9)
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Status Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Task Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("PENDING" to "Pending", "IN_PROGRESS" to "In Progress", "COMPLETED" to "Done").forEach { (code, label) ->
                                val selected = status == code
                                FilterChip(
                                    selected = selected,
                                    onClick = { status = code },
                                    label = { Text(label) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("status_chip_${code.lowercase()}"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Category & Custom Tags Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Category & Tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        val presetCategories = listOf("Work", "Personal", "Health", "Shopping", "Errands", "General")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(presetCategories) { cat ->
                                val selected = category.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { category = cat },
                                    label = { Text(cat) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("category_chip_${cat.lowercase()}")
                                )
                            }
                            if (category.isNotBlank() && !presetCategories.any { it.equals(category, ignoreCase = true) }) {
                                item {
                                    FilterChip(
                                        selected = true,
                                        onClick = { },
                                        label = { Text("# $category") },
                                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("category_chip_custom")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Add custom tag or category
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customCategoryInput,
                                onValueChange = { customCategoryInput = it },
                                placeholder = { Text("Assign custom tag or category...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_category_input"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (customCategoryInput.isNotBlank()) {
                                        category = customCategoryInput.trim()
                                        customCategoryInput = ""
                                    }
                                },
                                enabled = customCategoryInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("add_custom_category_button")
                            ) {
                                Text("Assign")
                            }
                        }
                    }
                }
            }

            // Reminder Sound & Tone Selection Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reminder Tone / Sound", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                            TextButton(
                                onClick = {
                                    try {
                                        val soundUri = when (reminderTone) {
                                            "URGENT_ALARM" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                                            "GENTLE_NOTIF" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                                            "PHONE_RINGTONE" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                                            else -> android.media.RingtoneManager.getDefaultUri(
                                                if (priority == "High") android.media.RingtoneManager.TYPE_ALARM
                                                else android.media.RingtoneManager.TYPE_NOTIFICATION
                                            )
                                        }
                                        android.media.RingtoneManager.getRingtone(context, soundUri)?.play()
                                    } catch (e: Exception) {
                                        // ignore
                                    }
                                }
                            ) {
                                Text("▶ Play Sound", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "DEFAULT" to "🔔 Default",
                                "URGENT_ALARM" to "🚨 Alarm",
                                "GENTLE_NOTIF" to "🎵 Chime",
                                "PHONE_RINGTONE" to "📞 Ringtone"
                            ).forEach { (key, label) ->
                                val selected = reminderTone == key
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        reminderTone = key
                                        try {
                                            val soundUri = when (key) {
                                                "URGENT_ALARM" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                                                "GENTLE_NOTIF" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                                                "PHONE_RINGTONE" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                                                else -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                                            }
                                            android.media.RingtoneManager.getRingtone(context, soundUri)?.play()
                                        } catch (e: Exception) {
                                            // ignore
                                        }
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Date and Time Picker Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Due Date & Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val currentDateText = dueDateMs?.let { dateFormatter.format(Date(it)) } ?: "No date set"
                        val currentTimeText = dueDateMs?.let { timeFormatter.format(Date(it)) } ?: "No time set"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_date_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(currentDateText, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_time_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(currentTimeText, maxLines = 1)
                            }
                        }

                        if (dueDateMs != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { dueDateMs = null },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Clear Due Date", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Habit Tracking Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Habit Tracking", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Repeat this task regularly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isHabit,
                                onCheckedChange = { isHabit = it },
                                modifier = Modifier.testTag("habit_tracking_switch")
                            )
                        }

                        if (isHabit) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Frequency", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Daily", "Weekly", "Weekdays").forEach { freq ->
                                    val selected = habitFrequency == freq
                                    FilterChip(
                                        selected = selected,
                                        onClick = { habitFrequency = freq },
                                        label = { Text(freq) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Google Maps Location & Proximity Geofencing Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Location (Google Maps)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Use Current Location (Wi-Fi / GPS)
                            TextButton(
                                onClick = {
                                    if (!hasLocationPermission) {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    } else {
                                        isFetchingCurrentLocation = true
                                        viewModel.getCurrentLocation { loc ->
                                            if (loc != null) {
                                                latitude = loc.latitude
                                                longitude = loc.longitude
                                                viewModel.reverseGeocode(loc.latitude, loc.longitude) { resolvedName ->
                                                    locationName = resolvedName
                                                    locationSearchQuery = resolvedName
                                                    isFetchingCurrentLocation = false
                                                }
                                            } else {
                                                isFetchingCurrentLocation = false
                                            }
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("form_detect_location_button")
                            ) {
                                if (isFetchingCurrentLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = "Current Location",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Auto-load Next Location Suggestions on screen open
                        LaunchedEffect(Unit) {
                            viewModel.searchPlacesAutocomplete(locationSearchQuery)
                        }

                        // Search Input
                        OutlinedTextField(
                            value = locationSearchQuery,
                            onValueChange = {
                                locationSearchQuery = it
                                if (it.isBlank()) {
                                    locationName = ""
                                    latitude = null
                                    longitude = null
                                }
                                viewModel.searchPlacesAutocomplete(it)
                            },
                            placeholder = { Text("Search location with Google Maps...") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (latitude != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (isSearchingPlaces) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (locationSearchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            locationSearchQuery = ""
                                            locationName = ""
                                            latitude = null
                                            longitude = null
                                            viewModel.clearPlaceSuggestions()
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear location",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_location_search_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Frequent Places Quick Chips & Preset Buttons
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frequent Places:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = {
                                        newFrequentName = locationName.ifBlank { locationSearchQuery }
                                        newFrequentAddress = locationSearchQuery
                                        showAddFrequentDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("+ Save Place", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (savedLocations.isNotEmpty()) {
                                    savedLocations.forEach { loc ->
                                        val isSelected = locationName.equals(loc.name, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                locationName = loc.name
                                                locationSearchQuery = loc.address.ifBlank { loc.name }
                                                geofenceRadius = loc.radiusMeters
                                                if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                                                    latitude = loc.latitude
                                                    longitude = loc.longitude
                                                } else {
                                                    coroutineScope.launch {
                                                        val resolved = viewModel.resolveLocationCoordinates(loc.address.ifBlank { loc.name }, fallbackToCurrentLocation = true)
                                                        if (resolved != null) {
                                                            latitude = resolved.second
                                                            longitude = resolved.third
                                                        }
                                                    }
                                                }
                                            },
                                            label = { Text("${loc.displayIcon} ${loc.name}", fontSize = 12.sp) }
                                        )
                                    }
                                } else {
                                    listOf(
                                        Triple("Home", "🏠", "Home"),
                                        Triple("Work", "💼", "Work"),
                                        Triple("Gym", "🏋️", "Gym"),
                                        Triple("Store", "🛒", "Grocery Store"),
                                        Triple("Coffee", "☕", "Coffee Shop"),
                                        Triple("School", "🏫", "School")
                                    ).forEach { (presetName, icon, query) ->
                                        FilterChip(
                                            selected = locationName.equals(presetName, ignoreCase = true),
                                            onClick = {
                                                locationName = presetName
                                                locationSearchQuery = query
                                                coroutineScope.launch {
                                                    val resolved = viewModel.resolveLocationCoordinates(query, fallbackToCurrentLocation = true)
                                                    if (resolved != null) {
                                                        latitude = resolved.second
                                                        longitude = resolved.third
                                                    }
                                                }
                                            },
                                            label = { Text("$icon $presetName", fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }

                        // Autocomplete Suggestions List
                        if (placeSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_place_suggestions_card"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    placeSuggestions.take(4).forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    locationName = suggestion.primaryText
                                                    locationSearchQuery = suggestion.primaryText
                                                    viewModel.clearPlaceSuggestions()
                                                    viewModel.fetchPlaceDetails(suggestion.placeId) { details ->
                                                        if (details != null && details.latLng != null) {
                                                            latitude = details.latLng.latitude
                                                            longitude = details.latLng.longitude
                                                            if (details.name.isNotBlank()) {
                                                                locationName = details.name
                                                            }
                                                        } else {
                                                            viewModel.searchLocation(suggestion.fullText) { name, lat, lng ->
                                                                locationName = name
                                                                latitude = lat
                                                                longitude = lng
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Business,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = suggestion.primaryText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (suggestion.secondaryText.isNotBlank()) {
                                                    Text(
                                                        text = suggestion.secondaryText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Selected Location Badge with Bookmark Option
                        if (latitude != null && longitude != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = locationName.ifBlank { "Selected Location" },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = String.format(Locale.US, "%.4f, %.4f", latitude, longitude),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val isAlreadySaved = savedLocations.any { it.name.equals(locationName, ignoreCase = true) }
                                            if (!isAlreadySaved && locationName.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.insertSavedLocation(
                                                            com.example.data.SavedLocation(
                                                                name = locationName,
                                                                address = locationSearchQuery,
                                                                latitude = latitude!!,
                                                                longitude = longitude!!,
                                                                radiusMeters = geofenceRadius
                                                            )
                                                        )
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.StarBorder,
                                                        contentDescription = "Bookmark as Frequent Place",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    latitude = null
                                                    longitude = null
                                                    locationName = ""
                                                    locationSearchQuery = ""
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Remove location",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Interactive Geofence Trigger Mode Selector
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Trigger Event Mode",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val triggerLabel = when (triggerDirection.uppercase()) {
                                        "DEPARTURE" -> "🚪 On Departure / Leaving"
                                        "PROXIMITY" -> "📡 When Approaching Near"
                                        else -> "📍 On Arrival / Entering"
                                    }
                                    Text(
                                        text = triggerLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple("ARRIVAL", "📍 Arrive", "Fires when you enter location area"),
                                        Triple("DEPARTURE", "🚪 Leave", "Fires when you exit location area"),
                                        Triple("PROXIMITY", "📡 Near", "Fires when nearby (300m)")
                                    ).forEach { (modeKey, modeLabel, _) ->
                                        val isSelected = triggerDirection.equals(modeKey, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                triggerDirection = modeKey
                                                isUserSelectedTriggerDirection = true
                                            },
                                            label = { Text(modeLabel, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (triggerDirection.uppercase()) {
                                        "DEPARTURE" -> "🚪 Notification will trigger as soon as you leave/exit this location."
                                        "PROXIMITY" -> "📡 Notification will trigger as soon as you approach near this location."
                                        else -> "📍 Notification will trigger as soon as you arrive at this location."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Gemini API AI Smart Schedule Optimizer Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini AI Schedule Optimizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Gemini analyzes your existing task list and commitments to find the optimal focus slot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = existingCommitments,
                            onValueChange = { existingCommitments = it },
                            placeholder = { Text("Optional commitments (e.g. Doctor appointment 1-2pm)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("commitments_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                viewModel.suggestOptimalSchedule(title, priority, existingCommitments)
                            },
                            enabled = title.isNotBlank() && !isSuggestingSchedule,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("suggest_schedule_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSuggestingSchedule) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini is calculating...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Suggest Optimal Schedule")
                            }
                        }

                        scheduleSuggestion?.let { suggestion ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "📍 Recommended Slot: ${suggestion.timeSlotText}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = suggestion.reasoning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val minutes = suggestion.suggestedMinutesFromNow ?: 120
                                            dueDateMs = System.currentTimeMillis() + minutes * 60 * 1000
                                        },
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("apply_ai_time_button")
                                    ) {
                                        Text("Apply Suggested Time")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Material Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDateMs ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMs ->
                            val currentCal = Calendar.getInstance()
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = dateMs
                                if (dueDateMs != null) {
                                    currentCal.timeInMillis = dueDateMs!!
                                    set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                                } else {
                                    set(Calendar.HOUR_OF_DAY, 17) // default 5:00 PM
                                    set(Calendar.MINUTE, 0)
                                }
                            }
                            dueDateMs = newCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material Time Picker Dialog
    if (showTimePicker) {
        val calendar = remember(dueDateMs) {
            Calendar.getInstance().apply {
                if (dueDateMs != null) timeInMillis = dueDateMs!!
                else {
                    set(Calendar.HOUR_OF_DAY, 17)
                    set(Calendar.MINUTE, 0)
                }
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val baseMs = dueDateMs ?: System.currentTimeMillis()
                        val newCal = Calendar.getInstance().apply {
                            timeInMillis = baseMs
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                        }
                        dueDateMs = newCal.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }

    if (showAddFrequentDialog) {
        AlertDialog(
            onDismissRequest = { showAddFrequentDialog = false },
            title = { Text("Save Frequent Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFrequentName,
                        onValueChange = { newFrequentName = it },
                        label = { Text("Location Name (e.g. Home, Gym)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFrequentAddress,
                        onValueChange = { newFrequentAddress = it },
                        label = { Text("Address / Google Maps Query") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Category Preset:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("HOME" to "🏠 Home", "WORK" to "💼 Work", "GYM" to "🏋️ Gym", "MARKET" to "🛒 Store", "SCHOOL" to "🏫 School", "CUSTOM" to "📍 Custom").forEach { (catKey, label) ->
                            FilterChip(
                                selected = newFrequentCategory == catKey,
                                onClick = { newFrequentCategory = catKey },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFrequentName.isNotBlank()) {
                            coroutineScope.launch {
                                val query = newFrequentAddress.ifBlank { newFrequentName }
                                val resolved = viewModel.resolveLocationCoordinates(query, fallbackToCurrentLocation = true)
                                val lat = resolved?.second ?: latitude ?: 0.0
                                val lng = resolved?.third ?: longitude ?: 0.0
                                viewModel.insertSavedLocation(
                                    com.example.data.SavedLocation(
                                        name = newFrequentName.trim(),
                                        address = query.trim(),
                                        latitude = lat,
                                        longitude = lng,
                                        category = newFrequentCategory
                                    )
                                )
                                locationName = newFrequentName.trim()
                                locationSearchQuery = query.trim()
                                if (lat != 0.0 || lng != 0.0) {
                                    latitude = lat
                                    longitude = lng
                                }
                                showAddFrequentDialog = false
                            }
                        }
                    },
                    enabled = newFrequentName.isNotBlank()
                ) {
                    Text("Save Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFrequentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
