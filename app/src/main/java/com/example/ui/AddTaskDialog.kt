package com.example.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    viewModel: TaskViewModel? = null,
    onDismiss: () -> Unit,
    onSaveTask: (Task, Boolean) -> Unit // task, shouldScheduleWorkManager
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") } // "High", "Medium", "Low"
    var reminderTone by remember { mutableStateOf("DEFAULT") } // "DEFAULT", "URGENT_ALARM", "GENTLE_NOTIF", "PHONE_RINGTONE"
    var status by remember { mutableStateOf("PENDING") } // "PENDING", "IN_PROGRESS", "COMPLETED"
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var scheduleWorkManagerNotification by remember { mutableStateOf(true) }
    var titleError by remember { mutableStateOf(false) }
    var naturalLanguagePrompt by remember { mutableStateOf("") }
    var isParsingPrompt by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Location state for Google Maps suggestions and Wi-Fi / GPS location
    val savedLocations = viewModel?.savedLocations?.collectAsState()?.value ?: emptyList()
    val placeSuggestions = viewModel?.placeSuggestions?.collectAsState()?.value ?: emptyList()
    val isSearchingPlaces = viewModel?.isSearchingPlaces?.collectAsState()?.value ?: false
    var locationSearchQuery by remember { mutableStateOf("") }
    var selectedLocationName by remember { mutableStateOf("") }
    var selectedLatitude by remember { mutableStateOf<Double?>(null) }
    var selectedLongitude by remember { mutableStateOf<Double?>(null) }
    var geofenceRadius by remember { mutableStateOf(250f) }
    var triggerDirection by remember { mutableStateOf("ARRIVAL") }
    var isUserSelectedTriggerDirection by remember { mutableStateOf(false) }
    var isFetchingCurrentLocation by remember { mutableStateOf(false) }
    var detectedCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    var showAddFrequentDialog by remember { mutableStateOf(false) }
    var newFrequentName by remember { mutableStateOf("") }
    var newFrequentAddress by remember { mutableStateOf("") }
    var newFrequentCategory by remember { mutableStateOf("CUSTOM") }

    // Dynamic Reactive Direction Trigger Auto-Detection (Only if user hasn't explicitly set it)
    LaunchedEffect(title, naturalLanguagePrompt, locationSearchQuery) {
        if (!isUserSelectedTriggerDirection) {
            val detected = GeminiTaskHelper.detectTriggerDirection("$title $naturalLanguagePrompt $locationSearchQuery")
            if (detected == "DEPARTURE") {
                triggerDirection = "DEPARTURE"
            }
        }
    }

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
            viewModel?.refreshLocation()
        }
    }

    // Voice Speech-to-Text API integration
    var pendingVoiceField by remember { mutableStateOf<String?>(null) } // "TITLE", "PROMPT", "DESC"

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                when (pendingVoiceField) {
                    "PROMPT" -> naturalLanguagePrompt = spoken
                    "DESC" -> description = spoken
                    else -> {
                        title = spoken
                        titleError = false
                    }
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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task or reminder...")
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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task or reminder...")
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

    val calendar = remember { Calendar.getInstance() }

    val formattedDueDate = remember(dueDate) {
        dueDate?.let {
            SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(it))
        } ?: "No due date set"
    }

    fun showDateTimePicker() {
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                TimePickerDialog(
                    context,
                    { _, selectedHour, selectedMinute ->
                        val selectedCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                            set(Calendar.SECOND, 0)
                        }
                        dueDate = selectedCal.timeInMillis
                    },
                    hour,
                    minute,
                    false
                ).show()
            },
            currentYear,
            currentMonth,
            currentDay
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_task_dialog"),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Task",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Natural Language Quick Auto-Fill Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_natural_language_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Natural Language Auto-Fill",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = naturalLanguagePrompt,
                                onValueChange = { naturalLanguagePrompt = it },
                                placeholder = {
                                    Text(
                                        "e.g. \"Doctor tomorrow 3pm high priority\"",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { launchVoiceInput("PROMPT") },
                                        modifier = Modifier.testTag("dialog_prompt_mic_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice input for prompt",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dialog_ai_prompt_field"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    if (naturalLanguagePrompt.isNotBlank()) {
                                        isParsingPrompt = true
                                        coroutineScope.launch {
                                            try {
                                                val frequentLocs = savedLocations.map { it.name }
                                                val parsed = GeminiTaskHelper.parseTaskFromNaturalLanguage(naturalLanguagePrompt, frequentLocations = frequentLocs)
                                                title = parsed.title
                                                if (!parsed.description.isNullOrBlank()) {
                                                    description = parsed.description
                                                }
                                                priority = parsed.priority
                                                parsed.minutesFromNow?.let { mins ->
                                                    dueDate = System.currentTimeMillis() + (mins * 60 * 1000)
                                                }
                                                detectedCandidates = parsed.candidateLocations
                                                if (!parsed.locationName.isNullOrBlank()) {
                                                    locationSearchQuery = parsed.locationName
                                                    selectedLocationName = parsed.locationName
                                                    triggerDirection = parsed.triggerDirection
                                                    viewModel?.searchPlacesAutocomplete(parsed.locationName)
                                                    viewModel?.resolveCoordinatesForLocation(parsed.locationName) { name, lat, lng ->
                                                        selectedLocationName = name
                                                        selectedLatitude = lat
                                                        selectedLongitude = lng
                                                    }
                                                }
                                                titleError = false
                                            } catch (_: Exception) {
                                            } finally {
                                                isParsingPrompt = false
                                            }
                                        }
                                    }
                                },
                                enabled = naturalLanguagePrompt.isNotBlank() && !isParsingPrompt,
                                modifier = Modifier.testTag("dialog_ai_autofill_button")
                            ) {
                                if (isParsingPrompt) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Fill")
                                }
                            }
                        }
                    }
                }

                // Task Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g. Finish quarterly project proposal") },
                    trailingIcon = {
                        IconButton(
                            onClick = { launchVoiceInput("TITLE") },
                            modifier = Modifier.testTag("dialog_title_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input for title",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Title is required", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_task_title"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Task Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Add key details, subtasks, or notes...") },
                    trailingIcon = {
                        IconButton(
                            onClick = { launchVoiceInput("DESC") },
                            modifier = Modifier.testTag("dialog_desc_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice input for description",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_task_description"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Priority Selection
                Column {
                    Text(
                        text = "Priority Level",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("High", Color(0xFFD32F2F), "dialog_priority_high"),
                            Triple("Medium", Color(0xFFE65100), "dialog_priority_medium"),
                            Triple("Low", Color(0xFF2E7D32), "dialog_priority_low")
                        ).forEach { (pLevel, color, tag) ->
                            val isSelected = priority == pLevel
                            val chipIcon = when (pLevel) {
                                "High" -> Icons.Default.KeyboardDoubleArrowUp
                                "Low" -> Icons.Default.KeyboardArrowDown
                                else -> Icons.Default.KeyboardArrowUp
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { priority = pLevel },
                                label = { Text(pLevel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = chipIcon,
                                        contentDescription = "$pLevel priority icon",
                                        tint = if (isSelected) color else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(tag)
                            )
                        }
                    }
                }

                // Due Date Picker & Quick Presets
                Column {
                    Text(
                        text = "Due Date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDateTimePicker() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = "Pick date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formattedDueDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (dueDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (dueDate != null) {
                                IconButton(
                                    onClick = { dueDate = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear date",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick presets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val c = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 18)
                                    set(Calendar.MINUTE, 0)
                                }
                                dueDate = c.timeInMillis
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Today (6pm)", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                val c = Calendar.getInstance().apply {
                                    add(Calendar.DAY_OF_YEAR, 1)
                                    set(Calendar.HOUR_OF_DAY, 9)
                                    set(Calendar.MINUTE, 0)
                                }
                                dueDate = c.timeInMillis
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tomorrow (9am)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Google Maps Location Suggestion & Wi-Fi / GPS Detection Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Location (Google Maps)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Use Current Location Button (Wi-Fi / Cell / GPS)
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
                                    viewModel?.getCurrentLocation { loc ->
                                        if (loc != null) {
                                            selectedLatitude = loc.latitude
                                            selectedLongitude = loc.longitude
                                            viewModel.reverseGeocode(loc.latitude, loc.longitude) { resolvedName ->
                                                selectedLocationName = resolvedName
                                                locationSearchQuery = resolvedName
                                                isFetchingCurrentLocation = false
                                            }
                                        } else {
                                            isFetchingCurrentLocation = false
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("dialog_detect_location_button")
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
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Auto-load Next Location Suggestions on open
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        viewModel?.searchPlacesAutocomplete(locationSearchQuery)
                    }

                    // Location Search Input with Google Maps Place Autocomplete
                    OutlinedTextField(
                        value = locationSearchQuery,
                        onValueChange = {
                            locationSearchQuery = it
                            if (it.isBlank()) {
                                selectedLocationName = ""
                                selectedLatitude = null
                                selectedLongitude = null
                            }
                            viewModel?.searchPlacesAutocomplete(it)
                        },
                        label = { Text("Search Place or Address") },
                        placeholder = { Text("e.g. Starbucks, Central Park, Home...") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                tint = if (selectedLatitude != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
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
                                        selectedLocationName = ""
                                        selectedLatitude = null
                                        selectedLongitude = null
                                        viewModel?.clearPlaceSuggestions()
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
                            .testTag("dialog_location_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Multi-location Disambiguation Card if multiple locations were found in the prompt
                    if (detectedCandidates.size > 1) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_disambiguation_card"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AltRoute,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Multiple places detected — choose geofence target:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    detectedCandidates.forEach { candidate ->
                                        val isSelected = selectedLocationName.equals(candidate, ignoreCase = true)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedLocationName = candidate
                                                locationSearchQuery = candidate
                                                viewModel?.searchPlacesAutocomplete(candidate)
                                                viewModel?.resolveCoordinatesForLocation(candidate) { name, lat, lng ->
                                                    selectedLocationName = name
                                                    selectedLatitude = lat
                                                    selectedLongitude = lng
                                                }
                                            },
                                            label = { Text(candidate, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Frequent Places Quick Chips & Quick Preset Buttons
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
                                    newFrequentName = selectedLocationName.ifBlank { locationSearchQuery }
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
                                    val isSelected = selectedLocationName.equals(loc.name, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedLocationName = loc.name
                                            locationSearchQuery = loc.address.ifBlank { loc.name }
                                            geofenceRadius = loc.radiusMeters
                                            if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                                                selectedLatitude = loc.latitude
                                                selectedLongitude = loc.longitude
                                            } else {
                                                coroutineScope.launch {
                                                    val resolved = viewModel?.resolveLocationCoordinates(loc.address.ifBlank { loc.name }, fallbackToCurrentLocation = true)
                                                    if (resolved != null) {
                                                        selectedLatitude = resolved.second
                                                        selectedLongitude = resolved.third
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
                                        selected = selectedLocationName.equals(presetName, ignoreCase = true),
                                        onClick = {
                                            selectedLocationName = presetName
                                            locationSearchQuery = query
                                            coroutineScope.launch {
                                                val resolved = viewModel?.resolveLocationCoordinates(query, fallbackToCurrentLocation = true)
                                                if (resolved != null) {
                                                    selectedLatitude = resolved.second
                                                    selectedLongitude = resolved.third
                                                }
                                            }
                                        },
                                        label = { Text("$icon $presetName", fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // Autocomplete Suggestions Dropdown
                    if (placeSuggestions.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_place_suggestions_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                placeSuggestions.take(4).forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedLocationName = suggestion.primaryText
                                                locationSearchQuery = suggestion.primaryText
                                                viewModel?.clearPlaceSuggestions()
                                                viewModel?.fetchPlaceDetails(suggestion.placeId) { details ->
                                                    if (details != null && details.latLng != null) {
                                                        selectedLatitude = details.latLng.latitude
                                                        selectedLongitude = details.latLng.longitude
                                                        if (details.name.isNotBlank()) {
                                                            selectedLocationName = details.name
                                                        }
                                                    } else {
                                                        viewModel?.searchLocation(suggestion.fullText) { name, lat, lng ->
                                                            selectedLocationName = name
                                                            selectedLatitude = lat
                                                            selectedLongitude = lng
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

                    // Active Selected Location Badge with Bookmark Option
                    if (selectedLatitude != null && selectedLongitude != null) {
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
                                                text = selectedLocationName.ifBlank { "Selected Location" },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.4f, %.4f", selectedLatitude, selectedLongitude),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isAlreadySaved = savedLocations.any { it.name.equals(selectedLocationName, ignoreCase = true) }
                                        if (!isAlreadySaved && selectedLocationName.isNotBlank() && viewModel != null) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.insertSavedLocation(
                                                        com.example.data.SavedLocation(
                                                            name = selectedLocationName,
                                                            address = locationSearchQuery,
                                                            latitude = selectedLatitude!!,
                                                            longitude = selectedLongitude!!,
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
                                                selectedLatitude = null
                                                selectedLongitude = null
                                                selectedLocationName = ""
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

                // Completion Status Selection
                Column {
                    Text(
                        text = "Initial Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Pair("PENDING", "Pending"),
                            Pair("IN_PROGRESS", "In Progress"),
                            Pair("COMPLETED", "Completed")
                        ).forEach { (statusKey, statusLabel) ->
                            FilterChip(
                                selected = status == statusKey,
                                onClick = { status = statusKey },
                                label = { Text(statusLabel) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Custom Reminder Tone Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reminder Tone / Sound",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                    // ignore preview error
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("▶ Play Tone", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            Pair("DEFAULT", "🔔 Default"),
                            Pair("URGENT_ALARM", "🚨 Urgent Alarm"),
                            Pair("GENTLE_NOTIF", "🎵 Gentle Chime"),
                            Pair("PHONE_RINGTONE", "📞 Ringtone")
                        ).forEach { (toneKey, toneLabel) ->
                            FilterChip(
                                selected = reminderTone == toneKey,
                                onClick = {
                                    reminderTone = toneKey
                                    try {
                                        val soundUri = when (toneKey) {
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
                                label = { Text(toneLabel, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }

                // WorkManager Push Notification Trigger Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "WorkManager Push Trigger",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Schedule local background notification for upcoming deadline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = scheduleWorkManagerNotification,
                            onCheckedChange = { scheduleWorkManagerNotification = it },
                            modifier = Modifier.testTag("dialog_workmanager_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val newTask = Task(
                        title = title.trim(),
                        description = description.trim().ifBlank { null },
                        priority = priority,
                        dueDate = dueDate,
                        status = status,
                        locationName = selectedLocationName.trim().ifBlank { locationSearchQuery.trim() }.ifBlank { null },
                        latitude = selectedLatitude,
                        longitude = selectedLongitude,
                        geofenceRadius = geofenceRadius,
                        triggerDirection = triggerDirection,
                        reminderTone = reminderTone
                    )
                    onSaveTask(newTask, scheduleWorkManagerNotification)
                },
                modifier = Modifier.testTag("dialog_add_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    )

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
                        if (newFrequentName.isNotBlank() && viewModel != null) {
                            coroutineScope.launch {
                                val query = newFrequentAddress.ifBlank { newFrequentName }
                                val resolved = viewModel.resolveLocationCoordinates(query, fallbackToCurrentLocation = true)
                                val lat = resolved?.second ?: selectedLatitude ?: 0.0
                                val lng = resolved?.third ?: selectedLongitude ?: 0.0
                                viewModel.insertSavedLocation(
                                    com.example.data.SavedLocation(
                                        name = newFrequentName.trim(),
                                        address = query.trim(),
                                        latitude = lat,
                                        longitude = lng,
                                        category = newFrequentCategory
                                    )
                                )
                                selectedLocationName = newFrequentName.trim()
                                locationSearchQuery = query.trim()
                                if (lat != 0.0 || lng != 0.0) {
                                    selectedLatitude = lat
                                    selectedLongitude = lng
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
