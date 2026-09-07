package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.Task
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
    var dueDateMs by remember(existingTask) { mutableStateOf(existingTask?.dueDate) }

    // Google Maps Location & Geofence state
    var locationName by remember(existingTask) { mutableStateOf(existingTask?.locationName ?: "") }
    var latitude by remember(existingTask) { mutableStateOf(existingTask?.latitude) }
    var longitude by remember(existingTask) { mutableStateOf(existingTask?.longitude) }
    var geofenceRadius by remember(existingTask) { mutableStateOf(existingTask?.geofenceRadius ?: 150f) }
    var locationSearchQuery by remember(existingTask) { mutableStateOf(existingTask?.locationName ?: "") }
    var isFetchingCurrentLocation by remember { mutableStateOf(false) }

    val placeSuggestions by viewModel.placeSuggestions.collectAsState()
    val isSearchingPlaces by viewModel.isSearchingPlaces.collectAsState()

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
                                    locationName = locationName.trim().ifBlank { null },
                                    latitude = latitude,
                                    longitude = longitude,
                                    geofenceRadius = geofenceRadius,
                                    reminderTone = reminderTone,
                                    isCompleted = status == "COMPLETED"
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
                            listOf("High", "Medium", "Low").forEach { p ->
                                val selected = priority.equals(p, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { priority = p },
                                    label = { Text(p) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("priority_chip_${p.lowercase()}"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = when (p.lowercase()) {
                                            "high" -> MaterialTheme.colorScheme.errorContainer
                                            "medium" -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
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

                        // Search Input
                        OutlinedTextField(
                            value = locationSearchQuery,
                            onValueChange = {
                                locationSearchQuery = it
                                if (it.isBlank()) {
                                    locationName = ""
                                    latitude = null
                                    longitude = null
                                    viewModel.clearPlaceSuggestions()
                                } else {
                                    viewModel.searchPlacesAutocomplete(it)
                                }
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

                        // Selected Location Badge
                        if (latitude != null && longitude != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${locationName.ifBlank { "Location" }} (${String.format(Locale.US, "%.4f, %.4f", latitude, longitude)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            latitude = null
                                            longitude = null
                                            locationName = ""
                                            locationSearchQuery = ""
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Remove location",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
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
}
