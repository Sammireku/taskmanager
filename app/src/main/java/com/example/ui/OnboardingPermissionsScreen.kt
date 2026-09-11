package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingPermissionsScreen(
    viewModel: TaskViewModel,
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current

    val isTermsAccepted by viewModel.isTermsAccepted.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val voiceGender by viewModel.voiceGender.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val voiceRate by viewModel.voiceRate.collectAsState()

    var currentStep by remember { mutableStateOf(if (!isTermsAccepted) 0 else 1) }

    // Permission tracking states
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        micGranted = result[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsGranted = result[Manifest.permission.POST_NOTIFICATIONS]
                ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🤖", fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (currentStep) {
                                0 -> "Terms of Service & Privacy"
                                1 -> "Permissions Setup"
                                2 -> "AI Voice & Name Setup"
                                else -> "Interactive App Tour"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    if (currentStep > 0 && isTermsAccepted) {
                        TextButton(
                            onClick = {
                                viewModel.completeOnboarding()
                                onOnboardingComplete()
                            }
                        ) {
                            Text("Skip Tour")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep -= 1 },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (currentStep == index) 12.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (currentStep == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStep == 0) {
                                viewModel.acceptTerms()
                                currentStep = 1
                            } else if (currentStep == 1) {
                                currentStep = 2
                            } else if (currentStep == 2) {
                                currentStep = 3
                            } else {
                                viewModel.completeOnboarding()
                                onOnboardingComplete()
                            }
                        },
                        enabled = if (currentStep == 0) isTermsAccepted else true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("onboarding_next_button")
                    ) {
                        Text(
                            text = when (currentStep) {
                                0 -> "I Agree & Continue"
                                1 -> "Next: Voice Setup"
                                2 -> "Next: Feature Tour"
                                else -> "Get Started 🚀"
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (currentStep == 3) Icons.Default.Check else Icons.Default.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> StepTermsAndAgreement(
                    isAccepted = isTermsAccepted,
                    onAcceptChange = { accepted ->
                        if (accepted) viewModel.acceptTerms()
                    }
                )
                1 -> StepPermissionsRequest(
                    micGranted = micGranted,
                    locationGranted = locationGranted,
                    notificationsGranted = notificationsGranted,
                    onRequestPermissions = {
                        val perms = mutableListOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    }
                )
                2 -> StepVoiceAndNameSetup(
                    userName = userName,
                    onNameChange = { viewModel.setUserName(it) },
                    voiceGender = voiceGender,
                    voicePitch = voicePitch,
                    voiceRate = voiceRate,
                    onVoiceSettingsChange = { g, p, r -> viewModel.setVoiceSettings(g, p, r) },
                    onTestVoice = { viewModel.testVoicePersona() }
                )
                else -> StepAppTourAndSyntaxGuide()
            }
        }
    }
}

@Composable
private fun StepTermsAndAgreement(
    isAccepted: Boolean,
    onAcceptChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Cobby AI!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your Smart Voice Task & Geofenced Daily Companion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "TERMS OF SERVICE & PRIVACY AGREEMENT",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = """
1. Local First & Privacy Protection:
Your tasks, locations, and personal schedules are stored locally on your device. We prioritize user privacy and security.

2. AI & Voice Input Usage:
Voice inputs and text prompts are processed to extract dates, times, locations, and priorities using secure Gemini AI services.

3. Location Services & Geofencing:
Location data is used exclusively on-device to trigger arrival and departure reminders, calculate proximity radar distances, and suggest nearby venues. Location data is never sold or shared.

4. Microphones & Speech Audio:
Microphone access is used solely when you tap the voice button or issue Google Assistant commands to transcribe tasks. Continuous audio recording is not performed.

5. User Responsibility:
You are responsible for managing your task alarms, location permissions, and personal schedule entries.

By checking the box below, you confirm that you have read, understood, and agreed to these Terms of Service and Privacy Policies.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAcceptChange(!isAccepted) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccepted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                1.dp,
                if (isAccepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAccepted,
                    onCheckedChange = { onAcceptChange(it) },
                    modifier = Modifier.testTag("accept_terms_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I agree to the Terms of Service & User Privacy Agreement",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StepPermissionsRequest(
    micGranted: Boolean,
    locationGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enable Core Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "To deliver smart voice input, location alarms, and timely reminders, Cobby works best when these permissions are enabled.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Permission Items
        PermissionCardItem(
            icon = Icons.Default.Mic,
            title = "Microphone Access",
            description = "Speak directly to Cobby or use voice input to log tasks hands-free.",
            isGranted = micGranted
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCardItem(
            icon = Icons.Default.LocationOn,
            title = "Location Access",
            description = "Triggers arrival/departure geofence alarms, proximity radar, and place search.",
            isGranted = locationGranted
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCardItem(
            icon = Icons.Default.Notifications,
            title = "Notification & Alarm Alerts",
            description = "Delivers alarm sounds, full-screen notifications, and morning focus briefings.",
            isGranted = notificationsGranted
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("grant_permissions_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Required Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionCardItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.dp,
            if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isGranted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGranted) "Granted" else "Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGranted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StepVoiceAndNameSetup(
    userName: String,
    onNameChange: (String) -> Unit,
    voiceGender: String,
    voicePitch: Float,
    voiceRate: Float,
    onVoiceSettingsChange: (String, Float, Float) -> Unit,
    onTestVoice: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🗣️", fontSize = 32.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Personalize Your AI Companion",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Set your name and choose a natural, human-sounding AI voice persona.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Name Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "YOUR NAME / NICKNAME",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = onNameChange,
                    placeholder = { Text("e.g. Milou") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voice Persona Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COBBY VOICE PERSONA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = onTestVoice,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Test Voice",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Gender Selection Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        Triple("MALE", "Male Voice 🧑", "Warm & Deeper Tone"),
                        Triple("FEMALE", "Female Voice 👩", "Clear Conversational"),
                        Triple("DEFAULT", "System Default ⚙️", "Standard TTS")
                    )

                    options.forEach { (key, label, sub) ->
                        val isSelected = voiceGender == key
                        Surface(
                            onClick = { onVoiceSettingsChange(key, voicePitch, voiceRate) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pitch & Speed Sliders
                Text(
                    text = "Voice Pitch: ${(voicePitch * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = voicePitch,
                    onValueChange = { onVoiceSettingsChange(voiceGender, it, voiceRate) },
                    valueRange = 0.75f..1.25f,
                    steps = 10
                )

                Text(
                    text = "Speech Rate / Speed: ${(voiceRate * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = voiceRate,
                    onValueChange = { onVoiceSettingsChange(voiceGender, voicePitch, it) },
                    valueRange = 0.75f..1.25f,
                    steps = 10
                )
            }
        }
    }
}

@Composable
private fun StepAppTourAndSyntaxGuide() {
    var tourTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Interactive Feature & Syntax Guide 💡",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Master Cobby in seconds with natural language syntax examples and button breakdowns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tour Category Tabs
        ScrollableTabRow(
            selectedTabIndex = tourTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = tourTab == 0, onClick = { tourTab = 0 }) {
                Text("🗣️ Voice & Wording", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = tourTab == 1, onClick = { tourTab = 1 }) {
                Text("📍 Geofence Alarms", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = tourTab == 2, onClick = { tourTab = 2 }) {
                Text("🛰️ Proximity Radar", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = tourTab == 3, onClick = { tourTab = 3 }) {
                Text("⚡ Assistant & Buttons", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (tourTab) {
            0 -> TourVoiceAndWordingTab()
            1 -> TourGeofenceTab()
            2 -> TourProximityRadarTab()
            else -> TourButtonsAndShortcutsTab()
        }
    }
}

@Composable
private fun TourVoiceAndWordingTab() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Natural Language Syntax & Examples",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Speak or type naturally. Gemini AI extracts due dates, times, priorities, categories, and locations automatically!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            val syntaxExamples = listOf(
                Pair("📅 Date & Time", "\"Remind me to submit project report tomorrow at 3:30 PM\""),
                Pair("🔴 Priority & Tags", "\"Meet Sarah at Starbucks next Tuesday 5pm high priority #work\""),
                Pair("📍 Arrival/Departure Geofence", "\"Buy groceries when I arrive at Whole Foods\""),
                Pair("🔁 Recurring Habit", "\"Morning workout every day at 7 AM #fitness\"")
            )

            syntaxExamples.forEach { (title, example) ->
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = example,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TourGeofenceTab() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Location Alarms & Geofencing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Set reminders that sound when you arrive or leave specific places like Home, Office, Gym, or Stores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text("• Arrival Alarms: Sound instantly as you enter the place boundary.", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text("• Departure Alarms: Alert you when leaving (e.g. \"Don't forget my keys when leaving Home\").", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text("• Saved Places: Quick-toggle locations under Saved Places in Settings or Task Creator.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TourProximityRadarTab() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live Proximity Distance Radar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "As you travel around town, Cobby continuously measures your distance to nearby task locations and shows live radar alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text("• Live Distance Badge: Shows exact distance (e.g. \"📍 450m away from Pharmacy\").", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text("• One-Tap Navigation: Tap the route icon to launch Google Maps directions immediately.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TourButtonsAndShortcutsTab() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Buttons & Hands-free Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            val buttonsList = listOf(
                Pair("➕ FAB Button", "Opens the Quick Task Creator or holds for Voice Mode."),
                Pair("🎙️ Floating Mic Button", "Tap to speak a task directly to Cobby."),
                Pair("🤖 Cobby Companion Bar", "Tap Cobby on Home screen for audio greetings or name personalization."),
                Pair("🗣️ Google Assistant", "Say \"Hey Google, add task in Daily Planner...\" anytime!"),
                Pair("⚡ Lock Screen Tile", "Quick Settings tile for 1-tap voice access without unlocking.")
            )

            buttonsList.forEach { (btn, desc) ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = btn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
                    Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
