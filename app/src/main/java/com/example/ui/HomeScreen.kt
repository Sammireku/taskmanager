package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.material.icons.filled.Settings
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
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
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
            viewModel.syncAllGeofences()
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
    val userName by viewModel.userName.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var showTourDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isBriefingExpanded by remember { mutableStateOf(false) }
    val cobbyMood by viewModel.cobbyMood.collectAsState()
    val cobbySpeech by viewModel.cobbySpeech.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var inlineNaturalLanguageText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Google Recorder directly integrated into mic icon (no popup dialog/sheet)
    var pendingVoiceTarget by remember { mutableStateOf("FAB") } // "FAB" or "INLINE"

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                if (pendingVoiceTarget == "INLINE") {
                    inlineNaturalLanguageText = spoken
                } else {
                    viewModel.parseAndAddTask(spoken, isVoiceInitiated = true) { task ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("🎤 Google Voice Added: \"${task.safeTitle}\"")
                        }
                    }
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task (e.g., 'Buy groceries at 5pm')")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Google Speech Recognition unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for Google voice input.")
            }
        }
    }

    fun startGoogleVoiceRecorder(target: String = "FAB") {
        pendingVoiceTarget = target
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task (e.g., 'Buy groceries at 5pm')")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Google Speech Recognition unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // React to external voice triggers (e.g. Google Assistant or Quick Shortcut)
    val externalVoiceTrigger by viewModel.triggerVoiceInputEvent.collectAsState()
    LaunchedEffect(externalVoiceTrigger) {
        if (externalVoiceTrigger != null) {
            viewModel.clearVoiceInputTrigger()
            startGoogleVoiceRecorder(target = "FAB")
        }
    }

    // Calculate progress stats
    val totalTasksCount = allTasks.size
    val completedTasksCount = allTasks.count { it.isDone }
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
                        if (userName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showNameDialog = true }
                                    .testTag("home_user_name_greeting")
                            ) {
                                Text(
                                    text = "Hello, $userName! 👋",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit name",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Planner",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(
                                    onClick = { showNameDialog = true },
                                    label = { Text("Set Name 👤", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(26.dp).testTag("set_name_chip")
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            modifier = Modifier.testTag("search_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Toggle Search",
                                tint = if (isSearchExpanded || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showTourDialog = true },
                            modifier = Modifier.testTag("app_tour_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = "App Tour & Syntax Guide",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onNavigateToDiagnostics,
                            modifier = Modifier.testTag("geofence_radar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Geofence Diagnostics & Radar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

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
                    onClick = { startGoogleVoiceRecorder("FAB") },
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
                // Unified Cobby AI Companion & Quick Task Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("cobby_companion_bar"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Top Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBriefingExpanded = !isBriefingExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🤖", fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Cobby AI Assistant",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (userName.isNotBlank()) {
                                            Text(
                                                text = " • $userName",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = cobbySpeech,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showNameDialog = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("cobby_personalize_name_button")
                                ) {
                                    Icon(
                                        imageVector = if (userName.isNotBlank()) Icons.Default.Face else Icons.Default.AccountCircle,
                                        contentDescription = "Personalize Name",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Collapsible Briefing & Progress Track
                        AnimatedVisibility(visible = isBriefingExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = dailyBriefing,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$completedTasksCount of $totalTasksCount tasks done",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { viewModel.refreshBriefing() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        if (isBriefingLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Refresh Briefing",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick AI Natural Language Task Bar
                        OutlinedTextField(
                            value = inlineNaturalLanguageText,
                            onValueChange = { inlineNaturalLanguageText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_task_input_field"),
                            placeholder = {
                                Text(
                                    "✨ Type or speak a task...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (inlineNaturalLanguageText.isNotBlank()) {
                                        if (isAiParsing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
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
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { startGoogleVoiceRecorder("INLINE") },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = "Google Voice Input",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Only show Proximity Radar when nearby tasks actually exist or location permissions are needed
            if (nearbyTasks.isNotEmpty() || errandClusters.isNotEmpty()) {
                item {
                    ProximityRadarCard(
                        nearbyTasks = nearbyTasks,
                        errandClusters = errandClusters,
                        hasLocationPermission = hasLocationPermission,
                        hasAnyLocationTasks = allTasks.any { it.latitude != null && !it.isDone },
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
            }

            // Expandable Search Bar
            if (isSearchExpanded || searchQuery.isNotEmpty()) {
                item {
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
                            IconButton(onClick = {
                                viewModel.setQuery("")
                                isSearchExpanded = false
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
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
                        showBottomSheet = false
                        viewModel.parseAndAddTask(rawText) { task ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Added: \"${task.safeTitle}\" via AI parsing!")
                            }
                        }
                    },
                    onOpenVoice = {
                        showBottomSheet = false
                        startGoogleVoiceRecorder("FAB")
                    },
                    onOpenForm = {
                        showBottomSheet = false
                        showAddTaskDialog = true
                    },
                    onDismiss = { showBottomSheet = false }
                )
            }
        }

        // Conversational AI Clarification Dialog (Disambiguation popup or voice)
        val clarificationData by viewModel.clarificationDialogData.collectAsState()
        clarificationData?.let { data ->
            ConversationalClarificationDialog(
                data = data,
                onSpeak = { viewModel.speakText(it, force = true) }
            )
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

        if (showTourDialog) {
            AppTourDialog(onDismiss = { showTourDialog = false })
        }

        // Personalize Name Dialog
        if (showNameDialog) {
            var dialogNameInput by remember { mutableStateOf(userName) }
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Personalize AI Companion", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "What should Cobby and your AI assistant call you?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = dialogNameInput,
                            onValueChange = { dialogNameInput = it },
                            label = { Text("Your Name or Nickname") },
                            placeholder = { Text("e.g., Milou") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            trailingIcon = {
                                if (dialogNameInput.isNotBlank()) {
                                    IconButton(onClick = { dialogNameInput = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_name_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cobby will address you by name in morning focus briefings, conversational voice answers, and celebrations!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setUserName(dialogNameInput)
                            showNameDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("AI companion personalized for ${dialogNameInput.ifBlank { "you" }}! ✨")
                            }
                        },
                        modifier = Modifier.testTag("dialog_save_name_button")
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel")
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
