package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.Task
import com.example.location.LocationHelper
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TaskViewModel,
    onTaskClick: (Int) -> Unit,
    onEditTask: (Int) -> Unit = {},
    onCreateTask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allTasks by viewModel.allTasks.collectAsState()
    val filteredTasks by viewModel.filteredTasks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val dailyBriefing by viewModel.dailyBriefing.collectAsState()
    val isBriefingLoading by viewModel.isBriefingLoading.collectAsState()
    val isAiParsing by viewModel.isAiParsing.collectAsState()
    val nearbyTasks by viewModel.nearbyTasks.collectAsState()
    val errandClusters by viewModel.errandClusters.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val swipeToDeleteEnabled by viewModel.swipeToDeleteEnabled.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val trashTasks by viewModel.trashTasks.collectAsState()
    var showThemeMenu by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var showTrashDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            viewModel.setCobbySpeech("Notification permission is required for reminders to ring!")
        }
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

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.refreshLocation()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) }
    val cobbyMood by viewModel.cobbyMood.collectAsState()
    val cobbySpeech by viewModel.cobbySpeech.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var inlineNaturalLanguageText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val voiceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Calculate progress stats
    val totalTasksCount = allTasks.size
    val completedTasksCount = allTasks.count { it.isCompleted }
    val progressFraction = if (totalTasksCount > 0) completedTasksCount.toFloat() / totalTasksCount else 0f
    val animatedProgress by animateFloatAsState(targetValue = progressFraction, label = "progress")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Daily Planner",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Theme & Accessibility Menu Button
                        Box {
                            IconButton(
                                onClick = { showThemeMenu = true },
                                modifier = Modifier.testTag("theme_switch_button")
                            ) {
                                Icon(
                                    imageVector = when (themeMode) {
                                        AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                                        AppThemeMode.DARK -> Icons.Default.DarkMode
                                    },
                                    contentDescription = "Theme and Accessibility",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                Text(
                                    text = "Theme & Accessibility",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("System Default") },
                                    onClick = {
                                        viewModel.setThemeMode(AppThemeMode.SYSTEM)
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.BrightnessAuto, contentDescription = null)
                                    },
                                    trailingIcon = if (themeMode == AppThemeMode.SYSTEM) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("Light Mode") },
                                    onClick = {
                                        viewModel.setThemeMode(AppThemeMode.LIGHT)
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LightMode, contentDescription = null)
                                    },
                                    trailingIcon = if (themeMode == AppThemeMode.LIGHT) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                                DropdownMenuItem(
                                    text = { Text("Dark Mode") },
                                    onClick = {
                                        viewModel.setThemeMode(AppThemeMode.DARK)
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.DarkMode, contentDescription = null)
                                    },
                                    trailingIcon = if (themeMode == AppThemeMode.DARK) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                                HorizontalDivider()
                                 DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Dynamic Color")
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = dynamicColorEnabled,
                                                onCheckedChange = { viewModel.toggleDynamicColor() },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    },
                                    onClick = { viewModel.toggleDynamicColor() },
                                    leadingIcon = {
                                        Icon(Icons.Default.Palette, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Swipe to Delete")
                                                Text("Allow swiping task cards left to delete", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = swipeToDeleteEnabled,
                                                onCheckedChange = { viewModel.toggleSwipeToDelete() },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    },
                                    onClick = { viewModel.toggleSwipeToDelete() },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Voice Output (TTS)")
                                                Text("Cobby speaks responses aloud", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Switch(
                                                checked = isTtsEnabled,
                                                onCheckedChange = { viewModel.toggleTts() },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }
                                    },
                                    onClick = { viewModel.toggleTts() },
                                    leadingIcon = {
                                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("📁 Backup & Restore (JSON)") },
                                    onClick = {
                                        showBackupRestoreDialog = true
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileDownload, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("🗑️ Trash Bin (${trashTasks.size})") },
                                    onClick = {
                                        showTrashDialog = true
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("🔊 Test Reminder Sound") },
                                    onClick = {
                                        viewModel.sendTestAlarmNotification(context)
                                        showThemeMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                    DropdownMenuItem(
                                        text = { Text("🔔 Grant Notification Permission", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            showThemeMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    )
                                }
                            }
                        }

                        // Progress percentage badge
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${(progressFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Small status indicator showing backend syncing vs Room offline-first mode
                SyncStatusIndicator(
                    syncStatus = syncStatus,
                    onSyncClick = { viewModel.triggerBackendSync() }
                )
            }
        },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { showVoiceSheet = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("voice_ai_fab")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Task AI")
                }

                ExtendedFloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Task") },
                    text = { Text("Add Task") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_task_fab")
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("notification_permission_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
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
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Notification Permission Needed",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Task reminders cannot ring until notification permissions are granted.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Grant", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            item {
                // Dedicated Native Language Parsing Textbox Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_natural_language_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Natural Language Parser",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Natural Language Task Input",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            TextButton(
                                onClick = { showAddTaskDialog = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp).testTag("open_form_dialog_button")
                            ) {
                                Text(
                                    text = "Full Form →",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = inlineNaturalLanguageText,
                            onValueChange = { inlineNaturalLanguageText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_task_input_field"),
                            placeholder = {
                                Text(
                                    "e.g. \"Doctor appointment tomorrow 3pm #Health\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (inlineNaturalLanguageText.isNotBlank()) {
                                        if (isAiParsing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    val textToParse = inlineNaturalLanguageText
                                                    inlineNaturalLanguageText = ""
                                                    viewModel.parseAndAddTask(textToParse) { task ->
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Added: \"${task.safeTitle}\" via AI parsing!")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.testTag("inline_ai_parse_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Send,
                                                    contentDescription = "Parse and Add Task",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { showVoiceSheet = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = "Voice Input",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                // AI Daily Briefing & Progress Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI Briefing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Smart Briefing",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = { viewModel.refreshBriefing() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                if (isBriefingLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh Briefing",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = dailyBriefing,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress track
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$completedTasksCount of $totalTasksCount tasks done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            item {
                // Proximity Aware Radar Banner & Errand Clustering
                ProximityRadarCard(
                    nearbyTasks = nearbyTasks,
                    errandClusters = errandClusters,
                    hasLocationPermission = hasLocationPermission,
                    hasAnyLocationTasks = allTasks.any { it.latitude != null && !it.isCompleted },
                    onRequestLocationPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onTaskClick = onTaskClick,
                    onCompleteTask = { task -> viewModel.toggleTaskCompletion(task) }
                )
            }

            item {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("task_search_field"),
                    placeholder = { Text("Search tasks or categories...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            item {
                // Dynamic Filter Chips (Presets + custom tags + priority/status)
                val dynamicFilterOptions = remember(allTasks) {
                    val presets = listOf("All", "Today", "High", "Work", "Personal", "Health")
                    val customCategories = allTasks.mapNotNull { it.category?.trim() }
                        .filter { cat ->
                            cat.isNotBlank() &&
                            !presets.any { it.equals(cat, ignoreCase = true) } &&
                            !cat.equals("Done", ignoreCase = true) &&
                            !cat.equals("General", ignoreCase = true)
                        }
                        .distinct()
                    presets + customCategories + listOf("Done")
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dynamicFilterOptions) { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setFilter(filter) },
                            label = {
                                val labelText = when (filter) {
                                    "High" -> "🔥 High Priority"
                                    "Work" -> "💼 Work"
                                    "Personal" -> "👤 Personal"
                                    "Health" -> "❤️ Health"
                                    "Done" -> "✅ Done"
                                    "All" -> "All"
                                    "Today" -> "Today"
                                    else -> "# $filter"
                                }
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("filter_chip_${filter.lowercase()}")
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Task List or Empty State
            if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No tasks match your search" else "No tasks in this view",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the + button below to create or auto-generate one!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    val distanceText = remember(task.latitude, task.longitude, userLocation) {
                        if (task.latitude != null && task.longitude != null && userLocation != null) {
                            val dist = LocationHelper.calculateDistance(
                                userLocation!!.latitude,
                                userLocation!!.longitude,
                                task.latitude!!,
                                task.longitude!!
                            )
                            LocationHelper.formatDistance(dist)
                        } else null
                    }

                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        SwipeableTaskItem(
                            task = task,
                            subtasksCount = viewModel.getSubTasks(task).let { subtasks ->
                                if (subtasks.isEmpty()) null else "${subtasks.count { it.isDone }}/${subtasks.size}"
                            },
                            distanceText = distanceText,
                            swipeToDeleteEnabled = swipeToDeleteEnabled,
                            onClick = { onTaskClick(task.id) },
                            onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                            onDelete = {
                                viewModel.deleteTask(task)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "\"${task.title}\" deleted",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreLastDeletedTask()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Quick Add Bottom Sheet with AI
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                QuickAddTaskBottomSheet(
                    isAiParsing = isAiParsing,
                    onAddTask = { rawText ->
                        viewModel.parseAndAddTask(rawText) {
                            showBottomSheet = false
                        }
                    },
                    onOpenVoice = {
                        showBottomSheet = false
                        showVoiceSheet = true
                    },
                    onOpenForm = {
                        showBottomSheet = false
                        showAddTaskDialog = true
                    },
                    onDismiss = { showBottomSheet = false }
                )
            }
        }

        // Voice Task AI Bottom Sheet
        if (showVoiceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showVoiceSheet = false },
                sheetState = voiceSheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                VoiceTaskBottomSheet(
                    isAiParsing = isAiParsing,
                    onParseAndAdd = { spokenText ->
                        viewModel.parseAndAddTask(spokenText) {
                            showVoiceSheet = false
                        }
                    },
                    onDismiss = { showVoiceSheet = false }
                )
            }
        }

        // Compose Add Task Dialog opened by FloatingActionButton
        if (showAddTaskDialog) {
            AddTaskDialog(
                viewModel = viewModel,
                onDismiss = { showAddTaskDialog = false },
                onSaveTask = { newTask, shouldScheduleWorkManager ->
                    showAddTaskDialog = false
                    viewModel.addTask(newTask)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Added \"${newTask.safeTitle}\" to Room DB!"
                        )
                    }
                }
            )
        }

        // Backup and Restore (JSON) Dialog
        if (showBackupRestoreDialog) {
            var exportJsonText by remember { mutableStateOf("") }
            var importJsonText by remember { mutableStateOf("") }
            var activeTab by remember { mutableStateOf(0) }
            var statusMessage by remember { mutableStateOf("") }

            LaunchedEffect(showBackupRestoreDialog) {
                exportJsonText = viewModel.exportTasksToJson()
            }

            AlertDialog(
                onDismissRequest = { showBackupRestoreDialog = false },
                title = { Text("Backup & Restore (JSON)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TabRow(selectedTabIndex = activeTab) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = { Text("Export") }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                text = { Text("Import") }
                            )
                        }

                        if (activeTab == 0) {
                            Text("Copy your local tasks JSON backup:", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = exportJsonText,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Tasks Backup JSON", exportJsonText)
                                    clipboard.setPrimaryClip(clip)
                                    statusMessage = "Copied JSON backup to clipboard!"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📋 Copy to Clipboard")
                            }
                        } else {
                            Text("Paste tasks JSON backup to restore into Room DB:", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = importJsonText,
                                onValueChange = { importJsonText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                placeholder = { Text("[{\"title\": \"Task 1\", ...}]") },
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val count = viewModel.importTasksFromJson(importJsonText)
                                        statusMessage = "Imported $count tasks into Room DB!"
                                    }
                                },
                                enabled = importJsonText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📥 Restore Tasks")
                            }
                        }
                        if (statusMessage.isNotBlank()) {
                            Text(statusMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBackupRestoreDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Trash Bin (30-day Retention Buffer) Dialog
        if (showTrashDialog) {
            AlertDialog(
                onDismissRequest = { showTrashDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trash Bin (${trashTasks.size})")
                        if (trashTasks.isNotEmpty()) {
                            TextButton(onClick = { viewModel.emptyTrash() }) {
                                Text("Empty Trash", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Tasks in Trash are kept for 30 days before being permanently purged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (trashTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Trash is empty! ✨", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                            ) {
                                items(trashTasks, key = { it.id }) { task ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(task.safeTitle, style = MaterialTheme.typography.titleMedium)
                                                if (task.deletedAt != null) {
                                                    val dateStr = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(task.deletedAt!!))
                                                    Text("Deleted: $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Button(
                                                onClick = { viewModel.restoreTask(task) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("Restore")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTrashDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskItem(
    task: Task,
    subtasksCount: String?,
    distanceText: String? = null,
    swipeToDeleteEnabled: Boolean = true,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleComplete()
                    false // Don't remove card completely, just toggle
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (swipeToDeleteEnabled) {
                        onDelete()
                        true
                    } else false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = swipeToDeleteEnabled,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        TaskListItem(
            task = task,
            onClick = onClick,
            onToggleComplete = onToggleComplete,
            distanceText = distanceText,
            subtasksCount = subtasksCount
        )
    }
}

@Composable
fun QuickAddTaskBottomSheet(
    isAiParsing: Boolean,
    onAddTask: (String) -> Unit,
    onOpenVoice: () -> Unit,
    onOpenForm: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Natural Language Task Add",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onOpenVoice,
                modifier = Modifier.testTag("switch_to_voice_button")
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Switch to Voice Input",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Type or tap the mic icon to speak: \"Doctor appointment tomorrow 3pm #Health\" or \"Buy groceries at Trader Joe's high priority\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_task_input_field"),
            placeholder = { Text("What do you want to accomplish?") },
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = onOpenForm,
            modifier = Modifier
                .align(Alignment.End)
                .testTag("open_full_form_button")
        ) {
            Text("Need date/time pickers & priority? Open Full Form →")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenVoice,
                modifier = Modifier
                    .height(52.dp)
                    .testTag("quick_sheet_voice_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Voice")
            }

            Button(
                onClick = { onAddTask(inputText) },
                enabled = inputText.isNotBlank() && !isAiParsing,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("confirm_schedule_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAiParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Gemini AI Parsing...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Smart Add")
                }
            }
        }
    }
}

@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (syncStatus) {
        SyncStatus.OFFLINE_ROOM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        SyncStatus.SYNCED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
    }

    val contentColor = when (syncStatus) {
        SyncStatus.OFFLINE_ROOM -> MaterialTheme.colorScheme.onSurfaceVariant
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.onPrimaryContainer
        SyncStatus.SYNCED -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        onClick = onSyncClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier.testTag("sync_status_indicator")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (syncStatus) {
                SyncStatus.OFFLINE_ROOM -> {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline Room Mode",
                        tint = contentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                SyncStatus.SYNCING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.8.dp,
                        color = contentColor
                    )
                }
                SyncStatus.SYNCED -> {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced with Backend",
                        tint = contentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = syncStatus.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
