package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import com.example.places.PlaceSuggestion
import com.example.places.PlaceDetails
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.SubTask
import com.example.data.Task
import com.example.location.LocationHelper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.HelpOutline
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Int,
    viewModel: TaskViewModel,
    onBack: () -> Unit,
    onEditTask: ((Int) -> Unit)? = null
) {
    val allTasks by viewModel.allTasks.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val task = allTasks.find { it.id == taskId }

    val coroutineScope = rememberCoroutineScope()

    if (task == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Task Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Task not found or was deleted.")
            }
        }
        return
    }

    val subtasks = viewModel.getSubTasks(task)
    var newSubtaskText by remember { mutableStateOf("") }
    var locationSearchQuery by remember { mutableStateOf("") }
    var isSearchingLocation by remember { mutableStateOf(false) }

    val placeSuggestions by viewModel.placeSuggestions.collectAsState()
    val isSearchingPlaces by viewModel.isSearchingPlaces.collectAsState()
    val selectedPlaceDetails by viewModel.selectedPlaceDetails.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var mapDisplayMode by remember { mutableStateOf("map") }
    var showMapAuthGuide by remember { mutableStateOf(false) }

    // Coordinates (defaults to Singapore coordinates if not set)
    val taskLatLng = remember(task.latitude, task.longitude) {
        LatLng(task.latitude ?: 1.3521, task.longitude ?: 103.8198)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(taskLatLng, 15f)
    }

    // Ensure Maps SDK is initialized safely
    LaunchedEffect(context) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(context)
        } catch (_: Exception) {}
    }

    // Keep camera synced when task coordinates change
    LaunchedEffect(taskLatLng) {
        try {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(taskLatLng, 15f))
        } catch (_: Exception) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(taskLatLng, 15f)
        }
    }

    val distanceToUser = remember(task.latitude, task.longitude, userLocation) {
        if (task.latitude != null && task.longitude != null && userLocation != null) {
            LocationHelper.calculateDistance(
                userLocation!!.latitude,
                userLocation!!.longitude,
                task.latitude!!,
                task.longitude!!
            )
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareBody = buildString {
                                append("📌 Task: ${task.safeTitle}\n")
                                if (task.description?.isNotBlank() == true) {
                                    append("📝 Description: ${task.description}\n")
                                }
                                if (task.dueDate != null) {
                                    val dateStr = java.text.SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date(task.dueDate!!))
                                    append("⏰ Due: $dateStr\n")
                                }
                                append("🔥 Priority: ${task.safePriority}\n")
                                append("🏷️ Category: ${task.safeCategory}\n")
                                if (!task.locationName.isNullOrBlank()) {
                                    append("📍 Location: ${task.locationName}\n")
                                }
                                if (subtasks.isNotEmpty()) {
                                    append("\nSubtasks:\n")
                                    subtasks.forEach { sub ->
                                        append(if (sub.isDone) "  [x] " else "  [ ] ")
                                        append("${sub.title}\n")
                                    }
                                }
                                append("\nShared from Cobby AI Daily Planner")
                            }

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Task: ${task.safeTitle}")
                                putExtra(Intent.EXTRA_TEXT, shareBody)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Task Details"))
                        },
                        modifier = Modifier.testTag("share_task_button")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Task",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (onEditTask != null) {
                        IconButton(
                            onClick = { onEditTask(task.id) },
                            modifier = Modifier.testTag("edit_task_button")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Task",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = {
                        viewModel.deleteTask(task)
                        onBack()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Task",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card with priority and completion toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (task.safePriority.lowercase()) {
                                    "high" -> MaterialTheme.colorScheme.errorContainer
                                    "medium" -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = "${task.safePriority} Priority",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = when (task.safePriority.lowercase()) {
                                        "high" -> MaterialTheme.colorScheme.onErrorContainer
                                        "medium" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleTaskCompletion(task) },
                                modifier = Modifier.testTag("detail_toggle_complete")
                            ) {
                                Icon(
                                    imageVector = if (task.isDone) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = "Toggle Complete",
                                    tint = if (task.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
                        )

                        if (!task.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (task.dueDate != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val formatted = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(task.dueDate))
                            Text(
                                text = "Due: $formatted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Subtasks / Breakdown Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtasks & Steps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FilledTonalButton(
                        onClick = { viewModel.generateAiSubtasksForTask(task) },
                        modifier = Modifier.testTag("ai_breakdown_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Break Down")
                    }
                }
            }

            // Subtask items
            if (subtasks.isEmpty()) {
                item {
                    Text(
                        text = "No subtasks yet. Break it down using AI or add steps below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(subtasks, key = { it.id }) { subtask ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = subtask.isDone,
                            onCheckedChange = { viewModel.toggleSubTask(task, subtask.id) }
                        )
                        Text(
                            text = subtask.title,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (subtask.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (subtask.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.removeSubTask(task, subtask.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Subtask",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Add manual subtask input
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        placeholder = { Text("Add new step...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newSubtaskText.isNotBlank()) {
                                viewModel.addManualSubTask(task, newSubtaskText)
                                newSubtaskText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Step", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Proximity & Geofencing Control Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (!task.locationName.isNullOrBlank()) task.locationName else "Location Geofence",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (distanceToUser != null) {
                                        Text(
                                            text = "Currently ${LocationHelper.formatDistance(distanceToUser)} away from you",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Use Current Location Button
                            IconButton(
                                onClick = {
                                    viewModel.refreshLocation()
                                    userLocation?.let { loc ->
                                        viewModel.reverseGeocode(loc.latitude, loc.longitude) { name ->
                                            viewModel.updateTask(
                                                task.copy(
                                                    latitude = loc.latitude,
                                                    longitude = loc.longitude,
                                                    locationName = name
                                                )
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Use My Current Location",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Google Places SDK Real-Time Autocomplete Search
                        OutlinedTextField(
                            value = locationSearchQuery,
                            onValueChange = {
                                locationSearchQuery = it
                                viewModel.searchPlacesAutocomplete(it)
                            },
                            placeholder = { Text("Search places with Google Places SDK...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("places_search_input"),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (isSearchingPlaces || isSearchingLocation) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else if (locationSearchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            locationSearchQuery = ""
                                            viewModel.clearPlaceSuggestions()
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Search")
                                    }
                                }
                            }
                        )

                        // Autocomplete Suggestions Dropdown
                        if (placeSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    placeSuggestions.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    locationSearchQuery = suggestion.primaryText
                                                    viewModel.clearPlaceSuggestions()
                                                    // Fetch full details
                                                    viewModel.fetchPlaceDetails(suggestion.placeId) { details ->
                                                        if (details != null && details.latLng != null) {
                                                            viewModel.updateTask(
                                                                task.copy(
                                                                    locationName = details.name,
                                                                    latitude = details.latLng.latitude,
                                                                    longitude = details.latLng.longitude
                                                                )
                                                            )
                                                        } else {
                                                            // Fallback geocode
                                                            viewModel.searchLocation(suggestion.fullText) { name, lat, lng ->
                                                                viewModel.updateTask(
                                                                    task.copy(
                                                                        locationName = name,
                                                                        latitude = lat,
                                                                        longitude = lng
                                                                    )
                                                                )
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
                                                modifier = Modifier.size(20.dp)
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
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }

                        // Display fetched business details if available
                        selectedPlaceDetails?.let { details ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = details.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        details.rating?.let { rating ->
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(text = "%.1f".format(rating), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    details.address?.let { addr ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "📍 $addr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    details.phoneNumber?.let { phone ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = phone, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    details.websiteUri?.let { website ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = website, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Trigger Direction Selector: Arrival vs Departure
                        Text(
                            text = "Notification Trigger Timing",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = task.safeTriggerDirection.equals("ARRIVAL", ignoreCase = true),
                                onClick = {
                                    viewModel.updateTask(task.copy(triggerDirection = "ARRIVAL"))
                                },
                                label = { Text("When I Arrive") },
                                leadingIcon = {
                                    Icon(Icons.Default.FlightLand, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = task.safeTriggerDirection.equals("DEPARTURE", ignoreCase = true),
                                onClick = {
                                    viewModel.updateTask(task.copy(triggerDirection = "DEPARTURE"))
                                },
                                label = { Text("When I Leave") },
                                leadingIcon = {
                                    Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Geofence Radius Slider (50m to 1000m)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Geofence Sensitivity Radius",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${task.geofenceRadius.roundToInt()} meters",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Slider(
                            value = task.geofenceRadius,
                            onValueChange = { newRadius ->
                                viewModel.updateTask(task.copy(geofenceRadius = newRadius))
                            },
                            valueRange = 50f..1000f,
                            steps = 18, // 50m increments
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("50m (Immediate)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("500m (Drive-by)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("1km (Area)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Interactive Geofence Radar & Google Maps Visualization
            item {
                val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "PulseScale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "PulseAlpha"
                )

                val isDeparture = task.safeTriggerDirection.equals("DEPARTURE", ignoreCase = true)
                val triggerColor = if (isDeparture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with Mode Switcher and Help
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (mapDisplayMode == "radar") Icons.Default.Radar else Icons.Default.Map,
                                    contentDescription = null,
                                    tint = triggerColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (mapDisplayMode == "radar") "Geofence Radar" else "Google Maps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SingleChoiceSegmentedButtonRow {
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        onClick = { mapDisplayMode = "radar" },
                                        selected = mapDisplayMode == "radar",
                                        label = { Text("Radar") }
                                    )
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        onClick = { mapDisplayMode = "map" },
                                        selected = mapDisplayMode == "map",
                                        label = { Text("Map") }
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { showMapAuthGuide = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.HelpOutline,
                                        contentDescription = "Map Setup Guide",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (mapDisplayMode == "radar") {
                            // Radar Canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                val crosshairColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

                                Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val maxRadius = (minOf(size.width, size.height) / 2f) * 0.9f

                                    // Concentric distance scale rings: 1000m, 500m, 250m, 50m
                                    val scales = listOf(1.0f, 0.5f, 0.25f, 0.05f)
                                    scales.forEach { scale ->
                                        drawCircle(
                                            color = gridColor,
                                            radius = maxRadius * scale,
                                            center = center,
                                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                                        )
                                    }

                                    // Crosshairs
                                    drawLine(
                                        color = crosshairColor,
                                        start = Offset(center.x - maxRadius, center.y),
                                        end = Offset(center.x + maxRadius, center.y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        color = crosshairColor,
                                        start = Offset(center.x, center.y - maxRadius),
                                        end = Offset(center.x, center.y + maxRadius),
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    // Geofence Radius Circle (proportional to 1000m max range)
                                    val radiusRatio = (task.geofenceRadius / 1000f).coerceIn(0.08f, 1.0f)
                                    val geofenceRadiusPx = maxRadius * radiusRatio

                                    // Pulsing wave
                                    drawCircle(
                                        color = triggerColor.copy(alpha = pulseAlpha),
                                        radius = geofenceRadiusPx * pulseScale,
                                        center = center
                                    )

                                    // Active Geofence Boundary
                                    drawCircle(
                                        color = triggerColor.copy(alpha = 0.15f),
                                        radius = geofenceRadiusPx,
                                        center = center
                                    )
                                    drawCircle(
                                        color = triggerColor,
                                        radius = geofenceRadiusPx,
                                        center = center,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )

                                    // Center Pin / Target Dot
                                    drawCircle(
                                        color = triggerColor,
                                        radius = 6.dp.toPx(),
                                        center = center
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.5.dp.toPx(),
                                        center = center
                                    )
                                }

                                // Legend & Geofence Status Badge
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${task.geofenceRadius.roundToInt()}m Trigger Radius",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = triggerColor
                                    )
                                    Text(
                                        text = if (isDeparture) "Triggers on Departure" else "Triggers on Arrival",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "1km boundary",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                )
                            }
                        } else {
                            // Live Google Maps Tiles
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "If map tiles are blank, ensure 'Maps SDK for Android' is enabled for your API key.",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { showMapAuthGuide = true },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Setup", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    GoogleMap(
                                        modifier = Modifier.fillMaxSize(),
                                        cameraPositionState = cameraPositionState,
                                        onMapClick = { clickedLatLng ->
                                            viewModel.reverseGeocode(clickedLatLng.latitude, clickedLatLng.longitude) { resolvedName ->
                                                viewModel.updateTask(
                                                    task.copy(
                                                        latitude = clickedLatLng.latitude,
                                                        longitude = clickedLatLng.longitude,
                                                        locationName = resolvedName
                                                    )
                                                )
                                            }
                                        }
                                    ) {
                                        Marker(
                                            state = MarkerState(position = taskLatLng),
                                            title = task.locationName ?: task.safeTitle,
                                            snippet = "${task.safeTriggerDirection}: ${task.geofenceRadius.roundToInt()}m radius"
                                        )
                                        Circle(
                                            center = taskLatLng,
                                            radius = task.geofenceRadius.toDouble(),
                                            fillColor = if (isDeparture) {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            },
                                            strokeColor = if (isDeparture) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            strokeWidth = 3f
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Coordinate details and Quick Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = String.format(Locale.US, "Lat: %.4f • Lng: %.4f", taskLatLng.latitude, taskLatLng.longitude),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = task.locationName ?: "Custom Pin",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString("${taskLatLng.latitude}, ${taskLatLng.longitude}"))
                                        Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                                }

                                Button(
                                    onClick = {
                                        val geoUri = Uri.parse("geo:${taskLatLng.latitude},${taskLatLng.longitude}?q=${taskLatLng.latitude},${taskLatLng.longitude}(${Uri.encode(task.safeTitle)})")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${taskLatLng.latitude},${taskLatLng.longitude}"))
                                            context.startActivity(webIntent)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Maps App", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showMapAuthGuide) {
        AlertDialog(
            onDismissRequest = { showMapAuthGuide = false },
            icon = { Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Google Maps Setup Guide") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "To enable interactive Google Maps tiles in this app:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Open Google Developer Console (console.developers.google.com).\n" +
                               "2. Select your project and navigate to 'APIs & Services' > 'Library'.\n" +
                               "3. Search for 'Maps SDK for Android' and click 'ENABLE'.\n" +
                               "4. In 'Credentials', ensure your Android API key exists with:\n" +
                               "   • Package name: com.example\n" +
                               "   • SHA-1: 35:A5:3A:96:E5:35:14:C9:14:A9:F0:83:33:16:59:14:0E:4E:EA:CD\n" +
                               "5. In AI Studio, configure MAPS_API_KEY in the Secrets panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "💡 The built-in Geofence Radar and 'Maps App' button work immediately without any API configuration.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showMapAuthGuide = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
