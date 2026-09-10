package com.example.ui

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedLocation
import com.example.data.SubTask
import com.example.data.Task
import com.example.data.RegressionCorpusManager
import com.example.gemini.ConversationalTaskExtraction
import com.example.gemini.GeminiTaskHelper
import com.example.gemini.RetrofitClient
import com.example.location.ErrandCluster
import com.example.location.GeofenceManager
import com.example.location.LocationHelper
import com.example.location.ProximityTaskInfo
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import com.example.places.PlacesService
import com.example.places.PlaceSuggestion
import com.example.places.PlaceDetails
import com.example.places.TaskPlaceInfo
import com.example.widget.TaskWidgetProvider
import com.example.notification.TaskAlarmScheduler
import com.example.work.TaskWorkScheduler
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.R
import com.example.location.GeofenceBroadcastReceiver
import androidx.core.app.NotificationCompat

data class ResolvedLocationCandidate(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val isSavedLocation: Boolean = false,
    val category: String = "CUSTOM"
)

enum class CobbyMood {
    IDLE,
    TALKING,
    CELEBRATING,
    EXCITED,
    THINKING
}

enum class SyncStatus(val label: String) {
    OFFLINE_ROOM("Local Room DB (Offline Mode)"),
    SYNCING("Synchronizing Cloud..."),
    SYNCED("Cloud Sync Active (Room + Firestore)")
}

class TaskViewModel(
    private val db: AppDatabase,
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "TaskViewModel"
    }

    val prefsManager = com.example.data.PreferencesManager(context)
    val authManager = com.example.auth.AuthManager(context)

    val locationHelper = LocationHelper(context.applicationContext)
    val geofenceManager = GeofenceManager(context.applicationContext)
    val placesService = PlacesService(context.applicationContext)
    val alarmScheduler = TaskAlarmScheduler(context.applicationContext)
    val workScheduler = TaskWorkScheduler(context.applicationContext)
    val firestoreRepository = com.example.data.FirestoreRepository()

    val soundFeedbackEnabled = MutableStateFlow(prefsManager.isSoundFeedbackEnabled)
    val bannerNotificationsEnabled = MutableStateFlow(prefsManager.isBannerNotificationsEnabled)
    val fullscreenAlarmEnabled = MutableStateFlow(prefsManager.isFullscreenAlarmEnabled)

    val regressionCorpusManager = com.example.data.RegressionCorpusManager(context.applicationContext)
    val regressionEntries = regressionCorpusManager.entries

    private val _clarificationDialogData = MutableStateFlow<ClarificationDialogData?>(null)
    val clarificationDialogData = _clarificationDialogData.asStateFlow()

    private var tts: TextToSpeech? = null
    var isTtsEnabled = MutableStateFlow(prefsManager.isSoundFeedbackEnabled)
        private set

    private var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        viewModelScope.launch {
            try {
                authManager.ensureAuthenticatedUser()
            } catch (_: Exception) {}
        }

        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                }
            }
        } catch (e: Exception) {
            // TTS engine unavailable
        }
        viewModelScope.launch {
            try {
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                db.taskDao().purgeOldTrash(thirtyDaysAgo)
            } catch (_: Exception) {}
            try {
                workScheduler.schedulePeriodicTrashPurge()
                workScheduler.scheduleDailyHabitReminders()
                workScheduler.schedulePeriodicCloudSync()
                workScheduler.schedulePeriodicGeofenceCalibration()
            } catch (_: Exception) {}
        }

        try {
            firestoreListener = firestoreRepository.listenToUserTasks { remoteTasks ->
                viewModelScope.launch {
                    for (task in remoteTasks) {
                        db.taskDao().insertTask(task)
                    }
                    TaskWidgetProvider.updateAllWidgets(context)
                }
            }
        } catch (_: Exception) {}
    }

    fun toggleTts() {
        isTtsEnabled.value = !isTtsEnabled.value
    }

    fun speakText(text: String, force: Boolean = false) {
        if ((isTtsEnabled.value || soundFeedbackEnabled.value || force) && text.isNotBlank()) {
            val clean = text.replace(Regex("[^\u0000-\u007F]"), "").trim()
            if (clean.isNotBlank()) {
                tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "CobbySpeech")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            firestoreListener?.remove()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }

    // Animated Cobby Character reactive states
    private val _cobbyMood = MutableStateFlow(CobbyMood.IDLE)
    val cobbyMood: StateFlow<CobbyMood> = _cobbyMood.asStateFlow()

    val userName = MutableStateFlow(prefsManager.userName)

    // Voice trigger requested from external actions (Google Assistant, App Shortcuts, or Deep Links)
    private val _triggerVoiceInputEvent = MutableStateFlow<Long?>(null)
    val triggerVoiceInputEvent: StateFlow<Long?> = _triggerVoiceInputEvent.asStateFlow()

    fun triggerVoiceInput() {
        _triggerVoiceInputEvent.value = System.currentTimeMillis()
    }

    fun clearVoiceInputTrigger() {
        _triggerVoiceInputEvent.value = null
    }

    private val _cobbySpeech = MutableStateFlow(
        if (prefsManager.userName.isNotBlank()) {
            "Hey ${prefsManager.userName}! I'm Cobby, your AI companion. Ready to conquer your day?"
        } else {
            "Hey there! I'm Cobby. Tell me your name so I can address you personally!"
        }
    )
    val cobbySpeech: StateFlow<String> = _cobbySpeech.asStateFlow()

    fun setUserName(name: String) {
        val trimmed = name.trim()
        prefsManager.userName = trimmed
        userName.value = trimmed
        val greeting = if (trimmed.isNotBlank()) {
            "Wonderful to meet you, $trimmed! I'm Cobby, your personal AI assistant. Let's make today productive!"
        } else {
            "Hey there! I'm Cobby. Tap me or hit the '+' button to log your tasks!"
        }
        _cobbySpeech.value = greeting
        speakText(greeting)
        refreshBriefing()
    }

    // Dynamic color & theme switching state
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(true)
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    // Swipe to delete task toggle
    private val _swipeToDeleteEnabled = MutableStateFlow(true)
    val swipeToDeleteEnabled: StateFlow<Boolean> = _swipeToDeleteEnabled.asStateFlow()

    // Offline-first Room vs Backend sync status indicator
    val isInternetLocationEnabled = MutableStateFlow(prefsManager.isInternetLocationEnabled)
    val isCloudSyncEnabled = MutableStateFlow(prefsManager.isCloudSyncEnabled)

    private val _syncStatus = MutableStateFlow(
        if (prefsManager.isCloudSyncEnabled) SyncStatus.SYNCED else SyncStatus.OFFLINE_ROOM
    )
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val placeSuggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val isSearchingPlaces = MutableStateFlow(false)
    val selectedPlaceDetails = MutableStateFlow<PlaceDetails?>(null)

    val scheduleSuggestionState = MutableStateFlow<com.example.gemini.ScheduleSuggestion?>(null)
    val isSuggestingSchedule = MutableStateFlow(false)

    val optimizedDailyScheduleState = MutableStateFlow<com.example.gemini.OptimizedDailySchedule?>(null)
    val isGeneratingDailySchedule = MutableStateFlow(false)

    val allTasks: StateFlow<List<Task>> = db.taskDao().getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedLocations: StateFlow<List<SavedLocation>> = db.savedLocationDao().getAllSavedLocations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val diagnosticLogs: StateFlow<List<com.example.data.GeofenceEventLog>> = db.geofenceEventLogDao().getAllLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val detectedLocationCandidates = MutableStateFlow<List<ResolvedLocationCandidate>>(emptyList())

    val trashTasks: StateFlow<List<Task>> = db.taskDao().getTrashTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow("All") // "All", "Today", "High", "Work", "Personal", "Done"

    val userLocation = MutableStateFlow<Location?>(null)

    val nearbyTasks: StateFlow<List<ProximityTaskInfo>> = combine(
        allTasks,
        userLocation
    ) { tasks, location ->
        if (location != null) {
            locationHelper.getNearbyTasks(location, tasks)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val errandClusters: StateFlow<List<ErrandCluster>> = allTasks.combine(userLocation) { tasks, _ ->
        locationHelper.findClusters(tasks, maxClusterDistanceMeters = 500f)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredTasks: StateFlow<List<Task>> = combine(
        allTasks,
        searchQuery,
        selectedFilter
    ) { tasks, query, filter ->
        tasks.filter { task ->
            val matchesQuery = query.isBlank() ||
                task.safeTitle.contains(query, ignoreCase = true) ||
                (task.description?.contains(query, ignoreCase = true) == true) ||
                (task.safeCategory.contains(query, ignoreCase = true)) ||
                (task.locationName?.contains(query, ignoreCase = true) == true)

            val matchesFilter = when (filter) {
                "All" -> true
                "Today" -> isDueToday(task.dueDate)
                "High" -> task.safePriority.equals("High", ignoreCase = true)
                "Work" -> task.safeCategory.equals("Work", ignoreCase = true)
                "Personal" -> task.safeCategory.equals("Personal", ignoreCase = true)
                "Done" -> task.isDone
                else -> task.safeCategory.equals(filter, ignoreCase = true)
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isAiParsing = MutableStateFlow(false)
    val isAiParsing: StateFlow<Boolean> = _isAiParsing.asStateFlow()

    private val _dailyBriefing = MutableStateFlow("Loading your focus for today...")
    val dailyBriefing: StateFlow<String> = _dailyBriefing.asStateFlow()

    private val _isBriefingLoading = MutableStateFlow(false)
    val isBriefingLoading: StateFlow<Boolean> = _isBriefingLoading.asStateFlow()

    private var lastDeletedTask: Task? = null

    init {
        // Automatically fetch initial briefing once tasks load and sync pending alarms
        viewModelScope.launch {
            allTasks.collect { tasks ->
                if (_dailyBriefing.value == "Loading your focus for today..." && tasks.isNotEmpty()) {
                    refreshBriefing()
                } else if (tasks.isEmpty()) {
                    _dailyBriefing.value = "Tap the + button to add your first smart task!"
                }
                // Register alarms for active upcoming tasks
                tasks.filter { !it.isDone && (it.dueDate ?: 0) > System.currentTimeMillis() }
                    .forEach { alarmScheduler.scheduleTaskAlarm(it) }
                // Register geofences for active location-bound tasks
                tasks.filter { !it.isDone && it.latitude != null && it.longitude != null }
                    .forEach { geofenceManager.registerTaskGeofence(it) }
            }
        }

        // Fetch location on startup and start healing/proximity checks
        refreshLocation()
        healSavedLocationsAndTasks()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefsManager.themeModeString = mode.name
        _themeMode.value = mode
    }

    fun toggleDynamicColor() {
        val newVal = !_dynamicColorEnabled.value
        prefsManager.isDynamicColorEnabled = newVal
        _dynamicColorEnabled.value = newVal
    }

    fun setSoundFeedbackEnabled(enabled: Boolean) {
        prefsManager.isSoundFeedbackEnabled = enabled
        soundFeedbackEnabled.value = enabled
        isTtsEnabled.value = enabled
    }

    fun setBannerNotificationsEnabled(enabled: Boolean) {
        prefsManager.isBannerNotificationsEnabled = enabled
        bannerNotificationsEnabled.value = enabled
    }

    fun setFullscreenAlarmEnabled(enabled: Boolean) {
        prefsManager.isFullscreenAlarmEnabled = enabled
        fullscreenAlarmEnabled.value = enabled
    }

    fun getUserId(): String = authManager.getCurrentUserId()

    fun isAnonymousUser(): Boolean = authManager.isAnonymous()

    suspend fun exportTasksToCsv(): String {
        val allTasksList = db.taskDao().getAllTasksList()
        val sb = StringBuilder()
        sb.append("ID,Title,Description,Priority,Category,Status,Due Date,Is Habit,Frequency,Location\n")
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        for (t in allTasksList) {
            val dueStr = if (t.dueDate != null) dateFormat.format(java.util.Date(t.dueDate!!)) else ""
            val cleanTitle = t.safeTitle.replace("\"", "\"\"")
            val cleanDesc = (t.description ?: "").replace("\"", "\"\"")
            val cleanCat = t.safeCategory.replace("\"", "\"\"")
            val cleanLoc = (t.locationName ?: "").replace("\"", "\"\"")
            sb.append("${t.id},\"$cleanTitle\",\"$cleanDesc\",\"${t.safePriority}\",\"$cleanCat\",\"${t.safeStatus}\",\"$dueStr\",${t.isHabit},\"${t.habitFrequency ?: ""}\",\"$cleanLoc\"\n")
        }
        return sb.toString()
    }

    fun setSwipeToDeleteEnabled(enabled: Boolean) {
        _swipeToDeleteEnabled.value = enabled
    }

    fun toggleSwipeToDelete() {
        _swipeToDeleteEnabled.value = !_swipeToDeleteEnabled.value
    }

    fun sendTestAlarmNotification(context: Context) {
        viewModelScope.launch {
            val testTask = Task(
                id = 99999,
                title = "🔔 Test Reminder Ring",
                description = "This is a test alarm notification with custom ringtone sound & vibration.",
                priority = "High",
                dueDate = System.currentTimeMillis() + 2000L,
                reminderTone = "URGENT_ALARM"
            )
            alarmScheduler.scheduleTaskAlarm(testTask)
            _cobbySpeech.value = "Test alarm scheduled for 2 seconds from now! Listen for the ringtone!"
        }
    }

    fun setInternetLocationEnabled(enabled: Boolean) {
        prefsManager.isInternetLocationEnabled = enabled
        isInternetLocationEnabled.value = enabled
    }

    fun toggleCloudSync(enabled: Boolean) {
        prefsManager.isCloudSyncEnabled = enabled
        isCloudSyncEnabled.value = enabled
        if (enabled) {
            triggerBackendSync()
        } else {
            _syncStatus.value = SyncStatus.OFFLINE_ROOM
            val msg = "Offline Local Persistence (Room DB) active. Your data stays 100% on-device."
            _cobbySpeech.value = msg
            speakText(msg)
        }
    }

    fun triggerBackendSync() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                // Enqueue constrained WorkManager task for reliable background delivery
                workScheduler.enqueueImmediateCloudSync()

                val localTasks = db.taskDao().getAllTasksList()
                val remoteTasks = firestoreRepository.syncTasks(localTasks)
                for (r in remoteTasks) {
                    db.taskDao().insertTask(r)
                }
                prefsManager.isCloudSyncEnabled = true
                isCloudSyncEnabled.value = true
                _syncStatus.value = SyncStatus.SYNCED
                val msg = "Firestore synchronized! All tasks backed up to cloud."
                _cobbySpeech.value = msg
                speakText(msg)
            } catch (e: Exception) {
                // Fall back to offline Room, WorkManager will retry when network is connected
                _syncStatus.value = SyncStatus.OFFLINE_ROOM
                val msg = "Saved locally in Room. Cloud sync queued for when network is available."
                _cobbySpeech.value = msg
            }
        }
    }

    fun runGeofenceCalibration() {
        viewModelScope.launch {
            workScheduler.enqueueImmediateCalibration()
            refreshLocation()
        }
    }

    fun simulateDwellEvent() {
        viewModelScope.launch {
            val tasks = allTasks.value.filter { it.latitude != null && it.longitude != null && !it.isDone }
            val targetTask = tasks.firstOrNull() ?: allTasks.value.firstOrNull()
            val loc = userLocation.value
            val lat = loc?.latitude ?: targetTask?.latitude ?: 37.422
            val lng = loc?.longitude ?: targetTask?.longitude ?: -122.084
            val title = targetTask?.safeTitle ?: "Home Chore Checklist"
            val locationName = targetTask?.locationName ?: "Home"

            db.geofenceEventLogDao().insertLog(
                com.example.data.GeofenceEventLog(
                    timestamp = System.currentTimeMillis(),
                    geofenceId = targetTask?.id?.toString() ?: "demo-99",
                    taskTitle = title,
                    transitionType = "DWELL",
                    dwellDurationMs = 30000L,
                    accuracyMeters = loc?.accuracy ?: 12f,
                    latitude = lat,
                    longitude = lng,
                    notes = "Simulated 30s dwell event verified at $locationName"
                )
            )

            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notif = NotificationCompat.Builder(context, "geofence_channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("⏱️ Dwelling at $locationName (Verified)")
                    .setContentText("Dwell test confirmed: 30s loitering satisfied for '$title'")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                nm.notify(8881, notif)
            } catch (_: Exception) {}
        }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch {
            db.geofenceEventLogDao().clearAllLogs()
        }
    }

    private fun calculateNextDueDate(currentDueDate: Long?, frequency: String?): Long {
        val cal = Calendar.getInstance()
        if (currentDueDate != null && currentDueDate > 0) {
            cal.timeInMillis = currentDueDate
        }
        when (frequency?.lowercase()) {
            "daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "weekly" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "weekdays" -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
            }
            "monthly" -> cal.add(Calendar.MONTH, 1)
            else -> cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    suspend fun exportTasksToJson(): String {
        val allTasks = db.taskDao().getAllTasksList()
        val jsonArray = org.json.JSONArray()
        for (t in allTasks) {
            val obj = org.json.JSONObject().apply {
                put("id", t.id)
                put("title", t.safeTitle)
                put("description", t.description.orEmpty())
                put("priority", t.safePriority)
                put("dueDate", t.dueDate ?: 0L)
                put("status", t.status)
                put("isHabit", t.isHabit)
                put("habitFrequency", t.habitFrequency.orEmpty())
                put("isDone", t.isDone)
                put("category", t.safeCategory)
                put("subtasksJson", t.subtasksJson.orEmpty())
                put("locationName", t.locationName.orEmpty())
                put("latitude", t.latitude ?: 0.0)
                put("longitude", t.longitude ?: 0.0)
                put("reminderTone", t.safeReminderTone)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    suspend fun importTasksFromJson(jsonString: String): Int {
        var count = 0
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val statusVal = if (obj.has("status")) obj.optString("status")
                                else obj.optString("completionStatus", if (obj.optBoolean("isCompleted", false)) "COMPLETED" else "PENDING")
                val importedTask = Task(
                    title = obj.optString("title", "Imported Task"),
                    description = obj.optString("description").ifBlank { null },
                    priority = obj.optString("priority", "Medium"),
                    dueDate = if (obj.has("dueDate") && obj.getLong("dueDate") > 0) obj.getLong("dueDate") else null,
                    status = statusVal,
                    isHabit = obj.optBoolean("isHabit", false),
                    habitFrequency = obj.optString("habitFrequency").ifBlank { null },
                    category = obj.optString("category").ifBlank { null },
                    subtasksJson = obj.optString("subtasksJson").ifBlank { null },
                    locationName = obj.optString("locationName").ifBlank { null },
                    latitude = if (obj.has("latitude") && obj.getDouble("latitude") != 0.0) obj.getDouble("latitude") else null,
                    longitude = if (obj.has("longitude") && obj.getDouble("longitude") != 0.0) obj.getDouble("longitude") else null,
                    reminderTone = obj.optString("reminderTone", "DEFAULT")
                )
                val newId = db.taskDao().insertTask(importedTask)
                val savedTask = importedTask.copy(id = newId.toInt())
                workScheduler.scheduleDueDateReminder(savedTask)
                alarmScheduler.scheduleTaskAlarm(savedTask)
                count++
            }
            TaskWidgetProvider.updateAllWidgets(context)
            val msg = "Successfully imported $count tasks from backup JSON!"
            _cobbySpeech.value = msg
            speakText(msg)
        } catch (e: Exception) {
            val msg = "Failed to import JSON: ${e.message}"
            _cobbySpeech.value = msg
            speakText("Failed to import JSON")
        }
        return count
    }

    fun refreshLocation() {
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                userLocation.value = loc
                evaluateProximityAlerts(loc)
            }
        }
    }

    fun getCurrentLocation(onResult: (Location?) -> Unit) {
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                userLocation.value = loc
                evaluateProximityAlerts(loc)
            }
            onResult(loc)
        }
    }

    fun setSimulatedLocation(lat: Double, lng: Double) {
        val simulated = Location("Simulated").apply {
            latitude = lat
            longitude = lng
        }
        userLocation.value = simulated
        evaluateProximityAlerts(simulated)
    }

    fun setQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: String) {
        selectedFilter.value = filter
    }

    fun setCobbySpeech(text: String) {
        _cobbySpeech.value = text
        speakText(text)
    }

    fun onCobbyCharacterClicked() {
        val name = userName.value
        val namePrefix = if (name.isNotBlank()) "$name, " else ""
        val nameSuffix = if (name.isNotBlank()) ", $name" else ""
        val quotes = listOf(
            "${namePrefix}ready to conquer your to-do list? I'm right here with you!",
            "Tasks stored safely offline in Room Database! ⚡",
            "WorkManager is standing by to alert you before deadlines$nameSuffix!",
            "Pro-tip${nameSuffix}: Tap the '+' button or mic to schedule a task!",
            if (name.isNotBlank()) "Small daily progress equals massive long-term results, $name! 💪" else "Small daily progress equals massive long-term results! 💪",
            "You're doing great$nameSuffix! Keep knocking out those goals!"
        )
        val selected = quotes.random()
        _cobbySpeech.value = selected
        speakText(selected)
        _cobbyMood.value = CobbyMood.TALKING
        viewModelScope.launch {
            delay(3500)
            if (_cobbyMood.value == CobbyMood.TALKING) {
                _cobbyMood.value = CobbyMood.IDLE
            }
        }
    }

    fun reactToTaskAdded(task: Task) {
        val name = userName.value
        val nameTag = if (name.isNotBlank()) "$name! " else ""
        val msg = "Awesome ${nameTag}Scheduled \"${task.safeTitle}\" with smart reminders! 🚀"
        _cobbySpeech.value = msg
        speakText(msg)
        _cobbyMood.value = CobbyMood.EXCITED
        viewModelScope.launch {
            delay(3500)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    fun reactToTaskCompleted(task: Task) {
        val name = userName.value
        val nameTag = if (name.isNotBlank()) " $name," else ""
        val msg = "Woohoo! Great job$nameTag \"${task.safeTitle}\" completed! High five! 🎉"
        _cobbySpeech.value = msg
        speakText(msg)
        _cobbyMood.value = CobbyMood.CELEBRATING
        viewModelScope.launch {
            delay(4500)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    fun reactToTaskDeleted(task: Task) {
        val name = userName.value
        val nameSuffix = if (name.isNotBlank()) ", $name" else ""
        val msg = "Moved \"${task.safeTitle}\" to Trash$nameSuffix."
        _cobbySpeech.value = msg
        speakText(msg)
        viewModelScope.launch {
            delay(2500)
            val nextName = if (userName.value.isNotBlank()) " ${userName.value}" else ""
            _cobbySpeech.value = "Hey$nextName! What should we tackle next?"
        }
    }

    fun createTask(
        title: String,
        description: String? = null,
        priority: String = "Medium",
        dueDate: Long? = null,
        status: String = "PENDING",
        category: String = "General"
    ) {
        val task = Task(
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate,
            status = status,
            category = category
        )
        addTask(task)
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            var taskToSave = task
            // Ensure coordinates are resolved if locationName is set
            if (!task.locationName.isNullOrBlank() && (task.latitude == null || task.latitude == 0.0 || task.longitude == null || task.longitude == 0.0)) {
                val resolved = resolveLocationCoordinates(task.locationName, fallbackToCurrentLocation = true)
                if (resolved != null && (resolved.second != 0.0 || resolved.third != 0.0)) {
                    taskToSave = task.copy(
                        locationName = resolved.first,
                        latitude = resolved.second,
                        longitude = resolved.third
                    )
                }
            }

            val id = db.taskDao().insertTask(taskToSave)
            val savedTask = taskToSave.copy(id = id.toInt())
            // WorkManager local push notification trigger for upcoming due dates
            workScheduler.scheduleDueDateReminder(savedTask)
            alarmScheduler.scheduleTaskAlarm(savedTask)
            if (savedTask.latitude != null && savedTask.longitude != null && !savedTask.isDone) {
                geofenceManager.registerTaskGeofence(savedTask)
                checkImmediateProximityOnTaskAdded(savedTask)
            }
            TaskWidgetProvider.updateAllWidgets(context)
            reactToTaskAdded(savedTask)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            var normalized = task.copy(
                status = if (task.isDone) "COMPLETED" else task.status
            )
            if (!normalized.isDone && !normalized.locationName.isNullOrBlank() && (normalized.latitude == null || normalized.latitude == 0.0 || normalized.longitude == null || normalized.longitude == 0.0)) {
                val resolved = resolveLocationCoordinates(normalized.locationName, fallbackToCurrentLocation = true)
                if (resolved != null && (resolved.second != 0.0 || resolved.third != 0.0)) {
                    normalized = normalized.copy(
                        locationName = resolved.first,
                        latitude = resolved.second,
                        longitude = resolved.third
                    )
                }
            }
            db.taskDao().updateTask(normalized)
            if (normalized.isDone) {
                workScheduler.cancelDueDateReminder(normalized.id)
                alarmScheduler.cancelTaskAlarm(normalized.id)
                geofenceManager.removeTaskGeofence(normalized.id)
            } else {
                workScheduler.scheduleDueDateReminder(normalized)
                alarmScheduler.scheduleTaskAlarm(normalized)
                if (normalized.latitude != null && normalized.longitude != null) {
                    geofenceManager.registerTaskGeofence(normalized)
                    checkImmediateProximityOnTaskAdded(normalized)
                } else {
                    geofenceManager.removeTaskGeofence(normalized.id)
                }
            }
            TaskWidgetProvider.updateAllWidgets(context)
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val nowCompleted = !task.isDone
            val updated = task.copy(
                status = if (nowCompleted) "COMPLETED" else "PENDING"
            )
            db.taskDao().updateTask(updated)
            if (nowCompleted) {
                workScheduler.cancelDueDateReminder(task.id)
                alarmScheduler.cancelTaskAlarm(task.id)
                geofenceManager.removeTaskGeofence(task.id)

                if (task.isHabit) {
                    val nextDueDate = calculateNextDueDate(task.dueDate, task.habitFrequency)
                    val nextOccurrence = task.copy(
                        id = 0,
                        dueDate = nextDueDate,
                        status = "PENDING"
                    )
                    val newId = db.taskDao().insertTask(nextOccurrence)
                    val savedNext = nextOccurrence.copy(id = newId.toInt())
                    workScheduler.scheduleDueDateReminder(savedNext)
                    alarmScheduler.scheduleTaskAlarm(savedNext)
                    if (savedNext.latitude != null && savedNext.longitude != null) {
                        geofenceManager.registerTaskGeofence(savedNext)
                    }
                    val dateFormatted = java.text.SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(java.util.Date(nextDueDate))
                    val speechMsg = "Habit \"${task.safeTitle}\" done! Next ${task.habitFrequency ?: "Daily"} scheduled for $dateFormatted 🔄"
                    _cobbySpeech.value = speechMsg
                    speakText(speechMsg)
                    _cobbyMood.value = CobbyMood.CELEBRATING
                } else {
                    reactToTaskCompleted(updated)
                }
            } else {
                workScheduler.scheduleDueDateReminder(updated)
                alarmScheduler.scheduleTaskAlarm(updated)
                if (updated.latitude != null && updated.longitude != null) {
                    geofenceManager.registerTaskGeofence(updated)
                }
            }
            TaskWidgetProvider.updateAllWidgets(context)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            lastDeletedTask = task
            val timestamp = System.currentTimeMillis()
            workScheduler.cancelDueDateReminder(task.id)
            alarmScheduler.cancelTaskAlarm(task.id)
            geofenceManager.removeTaskGeofence(task.id)
            db.taskDao().softDeleteTask(task.id, timestamp)
            firestoreRepository.saveTask(task.copy(deletedAt = timestamp))
            TaskWidgetProvider.updateAllWidgets(context)
            val msg = "Moved \"${task.safeTitle}\" to Trash! (30-day retention buffer)"
            _cobbySpeech.value = msg
            speakText(msg)
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            db.taskDao().restoreTask(task.id)
            firestoreRepository.saveTask(task.copy(deletedAt = null))
            workScheduler.scheduleDueDateReminder(task)
            alarmScheduler.scheduleTaskAlarm(task)
            if (task.latitude != null && task.longitude != null) {
                geofenceManager.registerTaskGeofence(task)
            }
            TaskWidgetProvider.updateAllWidgets(context)
            val msg = "Restored \"${task.safeTitle}\" from Trash!"
            _cobbySpeech.value = msg
            speakText(msg)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            db.taskDao().emptyTrash()
            val msg = "Emptied Trash bin!"
            _cobbySpeech.value = msg
            speakText(msg)
        }
    }

    fun restoreLastDeletedTask() {
        viewModelScope.launch {
            lastDeletedTask?.let {
                restoreTask(it)
                lastDeletedTask = null
            }
        }
    }

    fun triggerTestWorkNotification(task: Task) {
        workScheduler.triggerImmediateTestWork(task)
        _cobbySpeech.value = "Sent WorkManager push notification trigger for \"${task.safeTitle}\"!"
        _cobbyMood.value = CobbyMood.EXCITED
        viewModelScope.launch {
            delay(3000)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    /**
     * Natural Language Task Parsing with Gemini Conversational mode & schema enforcement.
     * When confidence is low or ambiguous spans exist (e.g. "at 5" -> "AM or PM?"),
     * it initiates a conversational clarification popup or voice prompt.
     */
    fun parseAndAddTask(
        inputText: String,
        isVoiceInitiated: Boolean = false,
        onComplete: ((Task) -> Unit)? = null
    ) {
        if (inputText.isBlank()) return
        viewModelScope.launch {
            _isAiParsing.value = true
            try {
                val frequentLocs = savedLocations.value.map { it.name }
                val recentTasks = allTasks.value.take(3)
                val currentName = userName.value
                val extraction = GeminiTaskHelper.parseConversationalTask(
                    utterance = inputText,
                    savedPlaces = frequentLocs,
                    recentReminders = recentTasks,
                    userName = currentName
                )

                val isAmbiguous = extraction.confidence.equals("low", true) ||
                    extraction.ambiguous_spans.isNotEmpty() ||
                    !extraction.clarification_question.isNullOrBlank()

                if (isAmbiguous) {
                    val question = extraction.clarification_question
                        ?: "Did you mean morning (AM) or evening (PM)?"
                    val options = if (extraction.clarification_options.isNotEmpty()) {
                        extraction.clarification_options
                    } else {
                        listOf("AM", "PM")
                    }

                    // Voice output when voice input was used or TTS is enabled
                    speakText(question, force = isVoiceInitiated)

                    _clarificationDialogData.value = ClarificationDialogData(
                        utterance = inputText,
                        extraction = extraction,
                        question = question,
                        options = options,
                        isVoiceInitiated = isVoiceInitiated,
                        onOptionChosen = { chosen ->
                            _clarificationDialogData.value = null
                            viewModelScope.launch {
                                finalizeClarifiedTask(
                                    utterance = inputText,
                                    extraction = extraction,
                                    userChoice = chosen,
                                    isVoiceInitiated = isVoiceInitiated,
                                    onComplete = onComplete
                                )
                            }
                        },
                        onConfirmAsIs = {
                            _clarificationDialogData.value = null
                            viewModelScope.launch {
                                finalizeTaskDirectly(
                                    inputText = inputText,
                                    extraction = extraction,
                                    isVoiceInitiated = isVoiceInitiated,
                                    onComplete = onComplete
                                )
                            }
                        },
                        onDismiss = {
                            _clarificationDialogData.value = null
                        }
                    )
                } else {
                    // High confidence, unambiguous
                    finalizeTaskDirectly(
                        inputText = inputText,
                        extraction = extraction,
                        isVoiceInitiated = isVoiceInitiated,
                        onComplete = onComplete
                    )
                }
            } catch (e: Exception) {
                val fallback = Task(title = inputText)
                db.taskDao().insertTask(fallback)
                TaskWidgetProvider.updateAllWidgets(context)
                onComplete?.invoke(fallback)
            } finally {
                _isAiParsing.value = false
            }
        }
    }

    private suspend fun finalizeClarifiedTask(
        utterance: String,
        extraction: ConversationalTaskExtraction,
        userChoice: String,
        isVoiceInitiated: Boolean,
        onComplete: ((Task) -> Unit)?
    ) {
        // Log to regression corpus (user corrected / clarified extraction)
        regressionCorpusManager.logCorrection(
            utterance = utterance,
            initialTask = extraction.task,
            initialLocation = extraction.location?.query,
            initialTime = extraction.time?.query,
            initialConfidence = extraction.confidence,
            ambiguousSpans = extraction.ambiguous_spans,
            userCorrection = "User selected: '$userChoice'",
            resolvedValue = userChoice
        )

        val finalTaskTitle = extraction.task.ifBlank { utterance }
        var finalLocationName = extraction.location?.query
        val finalTrigger = extraction.location?.trigger ?: "arrival"
        var calculatedDueDate: Long? = null

        // 1. Resolve time clarification (e.g. "5:00 AM", "5:00 PM", "am", "pm")
        val timeResolved = resolveClarifiedTime(extraction.time?.query ?: utterance, userChoice)
        if (timeResolved != null) {
            calculatedDueDate = timeResolved
        }

        // 2. Resolve location clarification if choice was a venue option
        val isLocClarification = extraction.clarification_options.any {
            it.equals(userChoice, ignoreCase = true)
        } && !userChoice.contains("am", ignoreCase = true) && !userChoice.contains("pm", ignoreCase = true)

        if (isLocClarification) {
            finalLocationName = userChoice
        }

        var lat: Double? = null
        var lng: Double? = null
        if (!finalLocationName.isNullOrBlank()) {
            val resolved = resolveLocationCoordinates(finalLocationName)
            if (resolved != null) {
                finalLocationName = resolved.first
                lat = resolved.second
                lng = resolved.third
            }
        }

        val task = Task(
            title = finalTaskTitle,
            dueDate = calculatedDueDate,
            category = inferCategory(utterance),
            priority = "Medium",
            locationName = finalLocationName,
            latitude = lat,
            longitude = lng,
            geofenceRadius = 150f,
            triggerDirection = if (finalTrigger.equals("departure", true)) "DEPARTURE" else "ARRIVAL"
        )

        val id = db.taskDao().insertTask(task)
        val savedTask = task.copy(id = id.toInt())
        alarmScheduler.scheduleTaskAlarm(savedTask)
        if (lat != null && lng != null) {
            geofenceManager.registerTaskGeofence(savedTask)
        }
        TaskWidgetProvider.updateAllWidgets(context)

        val currentName = userName.value
        val namePrefix = if (currentName.isNotBlank()) "$currentName, " else ""
        val confirmationSpeech = if (calculatedDueDate != null) {
            val timeStr = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(calculatedDueDate))
            "Got it, ${namePrefix}reminder set for $timeStr."
        } else if (!finalLocationName.isNullOrBlank()) {
            "Got it, ${namePrefix}reminder set for $finalLocationName."
        } else {
            "Added reminder for you, ${namePrefix}${savedTask.title}."
        }
        speakText(confirmationSpeech, force = isVoiceInitiated)

        withContext(Dispatchers.Main) {
            onComplete?.invoke(savedTask)
        }
    }

    private suspend fun finalizeTaskDirectly(
        inputText: String,
        extraction: ConversationalTaskExtraction,
        isVoiceInitiated: Boolean,
        onComplete: ((Task) -> Unit)?
    ) {
        var calculatedDueDate: Long? = null
        val timeQuery = extraction.time?.query
        if (!timeQuery.isNullOrBlank()) {
            calculatedDueDate = resolveClarifiedTime(null, timeQuery)
        }

        var finalLoc = extraction.location?.query
        var lat: Double? = null
        var lng: Double? = null
        if (!finalLoc.isNullOrBlank()) {
            val resolved = resolveLocationCoordinates(finalLoc)
            if (resolved != null) {
                finalLoc = resolved.first
                lat = resolved.second
                lng = resolved.third
            }
        }

        val triggerDir = if (extraction.location?.trigger.equals("departure", true)) "DEPARTURE" else "ARRIVAL"

        val task = Task(
            title = extraction.task.ifBlank { inputText },
            dueDate = calculatedDueDate,
            category = inferCategory(inputText),
            priority = "Medium",
            locationName = finalLoc,
            latitude = lat,
            longitude = lng,
            geofenceRadius = 150f,
            triggerDirection = triggerDir
        )

        val id = db.taskDao().insertTask(task)
        val savedTask = task.copy(id = id.toInt())
        alarmScheduler.scheduleTaskAlarm(savedTask)
        if (lat != null && lng != null) {
            geofenceManager.registerTaskGeofence(savedTask)
        }
        TaskWidgetProvider.updateAllWidgets(context)

        val currentName = userName.value
        val namePrefix = if (currentName.isNotBlank()) "$currentName, " else ""
        speakText("Added reminder for you, ${namePrefix}${savedTask.title}", force = isVoiceInitiated)

        withContext(Dispatchers.Main) {
            onComplete?.invoke(savedTask)
        }
    }

    private fun resolveClarifiedTime(initialTimeQuery: String?, userChoice: String): Long? {
        val lowerChoice = userChoice.lowercase(Locale.ROOT).trim()

        // 1. Explicit hours e.g. "5:00 PM", "5 PM", "5:30 am", "8 AM"
        val explicitTimeRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""", RegexOption.IGNORE_CASE)
        val match = explicitTimeRegex.find(userChoice)
        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val isPm = match.groupValues[3].equals("pm", true)
            return computeFutureMillis(hour, minute, isPm)
        }

        // 2. Relative keywords like "in 30 mins", "in 1 hour", "tomorrow"
        if (lowerChoice.contains("in 5 min")) return System.currentTimeMillis() + 5 * 60 * 1000
        if (lowerChoice.contains("in 15 min")) return System.currentTimeMillis() + 15 * 60 * 1000
        if (lowerChoice.contains("in 30 min")) return System.currentTimeMillis() + 30 * 60 * 1000
        if (lowerChoice.contains("in 1 hour") || lowerChoice.contains("in an hour")) return System.currentTimeMillis() + 60 * 60 * 1000
        if (lowerChoice.contains("tomorrow")) return System.currentTimeMillis() + 24 * 60 * 60 * 1000

        // 3. User choice was solely "AM" or "PM"
        if (lowerChoice == "am" || lowerChoice == "pm") {
            val isPm = lowerChoice == "pm"
            val bareHourRegex = Regex("""(\d{1,2})(?::(\d{2}))?""")
            val hourMatch = initialTimeQuery?.let { bareHourRegex.find(it) }
            val hour = hourMatch?.groupValues?.get(1)?.toIntOrNull() ?: 5
            val minute = hourMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            return computeFutureMillis(hour, minute, isPm)
        }

        return null
    }

    private fun computeFutureMillis(hour12: Int, minute: Int, isPm: Boolean): Long {
        val cal = Calendar.getInstance()
        var hour24 = hour12 % 12
        if (isPm) hour24 += 12
        cal.set(Calendar.HOUR_OF_DAY, hour24)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1) // Next day if already passed today
        }
        return cal.timeInMillis
    }

    private fun inferCategory(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("doctor") || lower.contains("dentist") || lower.contains("medicine") || lower.contains("pharmacy") || lower.contains("gym") -> "Health"
            lower.contains("buy") || lower.contains("groceries") || lower.contains("shop") || lower.contains("store") || lower.contains("market") -> "Shopping"
            lower.contains("meeting") || lower.contains("presentation") || lower.contains("report") || lower.contains("office") || lower.contains("work") -> "Work"
            lower.contains("study") || lower.contains("homework") || lower.contains("exam") || lower.contains("assignment") -> "Study"
            lower.contains("barbershop") || lower.contains("haircut") || lower.contains("dry clean") || lower.contains("bank") || lower.contains("errand") -> "Errands"
            else -> "Personal"
        }
    }

    /**
     * Generate AI Subtasks for a specific task.
     */
    fun generateAiSubtasksForTask(task: Task) {
        viewModelScope.launch {
            val generated = GeminiTaskHelper.generateSubtasks(task.title)
            if (generated.isNotEmpty()) {
                val existing = getSubTasks(task)
                val newSubtasks = existing + generated.map { SubTask(title = it) }
                val updatedTask = task.copy(
                    subtasksJson = RetrofitClient.jsonInstance.encodeToString(newSubtasks)
                )
                db.taskDao().updateTask(updatedTask)
                TaskWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    fun toggleSubTask(task: Task, subTaskId: String) {
        val subtasks = getSubTasks(task).map {
            if (it.id == subTaskId) it.copy(isDone = !it.isDone) else it
        }
        val updated = task.copy(
            subtasksJson = RetrofitClient.jsonInstance.encodeToString(subtasks)
        )
        updateTask(updated)
    }

    fun addManualSubTask(task: Task, title: String) {
        if (title.isBlank()) return
        val subtasks = getSubTasks(task) + SubTask(title = title.trim())
        val updated = task.copy(
            subtasksJson = RetrofitClient.jsonInstance.encodeToString(subtasks)
        )
        updateTask(updated)
    }

    fun removeSubTask(task: Task, subTaskId: String) {
        val subtasks = getSubTasks(task).filterNot { it.id == subTaskId }
        val updated = task.copy(
            subtasksJson = RetrofitClient.jsonInstance.encodeToString(subtasks)
        )
        updateTask(updated)
    }

    fun getSubTasks(task: Task): List<SubTask> {
        return try {
            task.subtasksJson?.let {
                RetrofitClient.jsonInstance.decodeFromString<List<SubTask>>(it)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private var placeSearchJob: Job? = null

    /**
     * Search Places using Google Places SDK Autocomplete with intelligent Geocoder fallback
     */
    fun searchPlacesAutocomplete(query: String) {
        placeSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            placeSuggestions.value = emptyList()
            isSearchingPlaces.value = false
            return
        }
        val bias = getLocationBiasCenter()
        placeSearchJob = viewModelScope.launch {
            delay(300L) // Debounce rapid keystrokes to minimize network usage and stay cost-effective
            isSearchingPlaces.value = true
            try {
                // 1. Instant local match from saved frequent places (0ms, 0 network, completely free)
                val savedList = db.savedLocationDao().getAllSavedLocationsList()
                val localMatches = savedList.filter {
                    it.name.contains(trimmed, ignoreCase = true) ||
                    it.address.contains(trimmed, ignoreCase = true)
                }.map { loc ->
                    PlaceSuggestion(
                        placeId = "saved_${loc.id}_${loc.latitude}_${loc.longitude}",
                        primaryText = "${loc.displayIcon} ${loc.name}",
                        secondaryText = loc.address.ifBlank { "Frequent Location (${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)})" },
                        fullText = loc.address.ifBlank { loc.name }
                    )
                }

                // 2. Fetch external suggestions (Places API or OSM / Geocoder fallback)
                val allowInternet = prefsManager.isInternetLocationEnabled
                val externalSuggestions = if (allowInternet) {
                    val placesPreds = placesService.getAutocompletePredictions(trimmed, biasCenter = bias)
                    if (placesPreds.isNotEmpty()) {
                        placesPreds
                    } else {
                        val geoList = locationHelper.searchPlacesList(trimmed, maxResults = 5)
                        geoList.map { item ->
                            PlaceSuggestion(
                                placeId = "geo_${item.second.first}_${item.second.second}",
                                primaryText = item.first,
                                secondaryText = "Lat: ${String.format(java.util.Locale.US, "%.4f", item.second.first)}, Lng: ${String.format(java.util.Locale.US, "%.4f", item.second.second)}",
                                fullText = item.first
                            )
                        }
                    }
                } else {
                    emptyList()
                }

                val combined = (localMatches + externalSuggestions).distinctBy { it.fullText.ifBlank { it.primaryText } }
                placeSuggestions.value = combined
            } catch (e: Exception) {
                Log.w(TAG, "Autocomplete search failed: ${e.message}")
            } finally {
                isSearchingPlaces.value = false
            }
        }
    }

    fun clearPlaceSuggestions() {
        placeSuggestions.value = emptyList()
    }

    /**
     * Fetch full Place details (address, phone, rating, website, lat/lng)
     */
    fun fetchPlaceDetails(placeId: String, onResult: ((PlaceDetails?) -> Unit)? = null) {
        viewModelScope.launch {
            if (placeId.startsWith("saved_")) {
                val parts = placeId.removePrefix("saved_").split("_")
                val id = parts.getOrNull(0)?.toIntOrNull()
                val lat = parts.getOrNull(1)?.toDoubleOrNull()
                val lng = parts.getOrNull(2)?.toDoubleOrNull()
                val savedLoc = if (id != null) db.savedLocationDao().getSavedLocationById(id) else null
                val finalLat = lat ?: savedLoc?.latitude ?: 0.0
                val finalLng = lng ?: savedLoc?.longitude ?: 0.0
                val details = PlaceDetails(
                    placeId = placeId,
                    name = savedLoc?.name ?: "Frequent Place",
                    address = savedLoc?.address ?: "Saved Place",
                    phoneNumber = null,
                    websiteUri = null,
                    rating = null,
                    latLng = com.google.android.gms.maps.model.LatLng(finalLat, finalLng)
                )
                selectedPlaceDetails.value = details
                onResult?.invoke(details)
                return@launch
            }
            if (placeId.startsWith("geo_")) {
                val parts = placeId.removePrefix("geo_").split("_")
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lng = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lng != null) {
                    val address = locationHelper.getAddressFromCoordinates(lat, lng)
                    val details = PlaceDetails(
                        placeId = placeId,
                        name = address,
                        address = address,
                        phoneNumber = null,
                        websiteUri = null,
                        rating = null,
                        latLng = com.google.android.gms.maps.model.LatLng(lat, lng)
                    )
                    selectedPlaceDetails.value = details
                    onResult?.invoke(details)
                    return@launch
                }
            }
            val details = placesService.fetchPlaceDetails(placeId)
            selectedPlaceDetails.value = details
            onResult?.invoke(details)
        }
    }

    fun insertSavedLocation(location: SavedLocation, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = db.savedLocationDao().insertSavedLocation(location)
            onComplete?.invoke(id)
        }
    }

    fun updateSavedLocation(location: SavedLocation) {
        viewModelScope.launch {
            db.savedLocationDao().updateSavedLocation(location)
        }
    }

    fun deleteSavedLocation(location: SavedLocation) {
        viewModelScope.launch {
            db.savedLocationDao().deleteSavedLocation(location)
        }
    }

    fun deleteSavedLocationById(id: Int) {
        viewModelScope.launch {
            db.savedLocationDao().deleteById(id)
        }
    }

    fun getLocationBiasCenter(): LatLng? {
        val currentLoc = userLocation.value
        if (currentLoc != null) {
            return LatLng(currentLoc.latitude, currentLoc.longitude)
        }
        val saved = savedLocations.value
        val homeOrWork = saved.firstOrNull { it.category.equals("HOME", ignoreCase = true) }
            ?: saved.firstOrNull { it.category.equals("WORK", ignoreCase = true) }
            ?: saved.firstOrNull()
        return homeOrWork?.let { LatLng(it.latitude, it.longitude) }
    }

    suspend fun resolveLocationCandidates(query: String): List<ResolvedLocationCandidate> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<ResolvedLocationCandidate>()
        val trimmed = query.trim()

        // 1. Saved frequent locations check (gazetteer fast-path)
        val savedList = db.savedLocationDao().getAllSavedLocationsList()
        for (saved in savedList) {
            if (saved.name.contains(trimmed, ignoreCase = true) || trimmed.contains(saved.name, ignoreCase = true)) {
                results.add(
                    ResolvedLocationCandidate(
                        name = saved.name,
                        address = saved.address.ifBlank { "Saved Location (${saved.category})" },
                        latitude = saved.latitude,
                        longitude = saved.longitude,
                        radiusMeters = saved.radiusMeters,
                        isSavedLocation = true,
                        category = saved.category
                    )
                )
            }
        }

        // 2. Bias center from user location or Home/Work
        val bias = getLocationBiasCenter()

        // 3. Places API with location bias
        try {
            val places = placesService.getCandidatePlaces(trimmed, biasCenter = bias, maxCandidates = 3)
            for (p in places) {
                if (p.latLng != null && results.none { abs(it.latitude - p.latLng.latitude) < 0.0001 && abs(it.longitude - p.latLng.longitude) < 0.0001 }) {
                    results.add(
                        ResolvedLocationCandidate(
                            name = p.name,
                            address = p.address ?: p.name,
                            latitude = p.latLng.latitude,
                            longitude = p.latLng.longitude,
                            radiusMeters = 150f,
                            isSavedLocation = false,
                            category = "VENUE"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Places candidate lookup failed for $trimmed", e)
        }

        // 4. Geocoder fallback if still empty
        if (results.isEmpty()) {
            try {
                val currentLoc = userLocation.value ?: locationHelper.getCurrentLocation()
                val geo = locationHelper.searchPlace(trimmed, currentLoc)
                if (geo != null) {
                    results.add(
                        ResolvedLocationCandidate(
                            name = geo.first,
                            address = geo.first,
                            latitude = geo.second.first,
                            longitude = geo.second.second,
                            radiusMeters = 150f,
                            isSavedLocation = false,
                            category = "GEO"
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        return results
    }

    /**
     * Resolves coordinates for any extracted or typed location name using a multi-tiered strategy:
     * 1. User's saved frequent locations (exact/fuzzy match)
     * 2. Google Places SDK autocomplete + details with location bias
     * 3. Proximity-biased Geocoder using user's current location
     * 4. Relative location mapping bound to current location
     */
    suspend fun resolveLocationCoordinates(
        locationName: String,
        fallbackToCurrentLocation: Boolean = true
    ): Triple<String, Double, Double>? {
        if (locationName.isBlank()) return null

        val currentLoc = userLocation.value ?: locationHelper.getCurrentLocation()
        if (currentLoc != null && userLocation.value == null) {
            userLocation.value = currentLoc
        }

        // Tier 0: Check user's saved frequent locations first (instant zero-latency match)
        var matchedSavedLocation: SavedLocation? = null
        try {
            val savedList = db.savedLocationDao().getAllSavedLocationsList()
            val match = savedList.firstOrNull {
                it.name.equals(locationName.trim(), ignoreCase = true) ||
                locationName.contains(it.name, ignoreCase = true)
            }
            if (match != null) {
                matchedSavedLocation = match
                if (match.latitude != 0.0 || match.longitude != 0.0) {
                    return Triple(match.name, match.latitude, match.longitude)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking saved locations for $locationName", e)
        }

        var resolvedResult: Triple<String, Double, Double>? = null

        // Tier 1: Try Google Places SDK with Location Bias
        if (resolvedResult == null) {
            try {
                val bias = getLocationBiasCenter()
                val suggestions = placesService.getAutocompletePredictions(locationName, biasCenter = bias)
                if (suggestions.isNotEmpty()) {
                    val top = suggestions.first()
                    val details = placesService.fetchPlaceDetails(top.placeId)
                    if (details?.latLng != null) {
                        val resolvedName = if (details.name.isNotBlank()) details.name else top.primaryText
                        resolvedResult = Triple(resolvedName, details.latLng.latitude, details.latLng.longitude)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Places SDK lookup failed for $locationName", e)
            }
        }

        // Tier 2: Try Geocoder with proximity bias from current user location
        if (resolvedResult == null) {
            try {
                val geoResult = locationHelper.searchPlace(locationName, currentLoc)
                if (geoResult != null && (geoResult.second.first != 0.0 || geoResult.second.second != 0.0)) {
                    resolvedResult = Triple(geoResult.first, geoResult.second.first, geoResult.second.second)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder searchPlace failed for $locationName", e)
            }
        }

        // Tier 3: Relative or Local Place Names ("Home", "Office", "Work", "Gym", "Here", "Current Location")
        if (resolvedResult == null) {
            val lower = locationName.trim().lowercase(Locale.ROOT)
            val isRelative = lower in listOf(
                "home", "office", "work", "workplace", "gym", "here", "current location", "my location", "station"
            )
            if (isRelative && currentLoc != null) {
                val titleCased = locationName.trim().replaceFirstChar { it.uppercase() }
                resolvedResult = Triple(titleCased, currentLoc.latitude, currentLoc.longitude)
            }
        }

        // Tier 4: Fallback binding to current location so geofence can still be established
        if (resolvedResult == null && fallbackToCurrentLocation && currentLoc != null) {
            val titleCased = locationName.trim().replaceFirstChar { it.uppercase() }
            resolvedResult = Triple(titleCased, currentLoc.latitude, currentLoc.longitude)
        }

        // Heal matched saved location if it previously lacked valid coordinates
        if (resolvedResult != null && matchedSavedLocation != null && matchedSavedLocation.latitude == 0.0 && matchedSavedLocation.longitude == 0.0) {
            try {
                db.savedLocationDao().updateSavedLocation(
                    matchedSavedLocation.copy(
                        latitude = resolvedResult.second,
                        longitude = resolvedResult.third,
                        address = matchedSavedLocation.address.ifBlank { resolvedResult.first }
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed healing saved location $locationName", e)
            }
        }

        return resolvedResult
    }

    fun resolveCoordinatesForLocation(locationName: String, onResult: (name: String, lat: Double, lng: Double) -> Unit) {
        viewModelScope.launch {
            val res = resolveLocationCoordinates(locationName)
            if (res != null) {
                onResult(res.first, res.second, res.third)
            }
        }
    }

    fun syncAllGeofences() {
        viewModelScope.launch {
            allTasks.value.filter { !it.isDone && it.latitude != null && it.longitude != null }
                .forEach { geofenceManager.registerTaskGeofence(it) }
        }
    }

    fun searchLocation(query: String, onResult: (name: String, lat: Double, lng: Double) -> Unit) {
        viewModelScope.launch {
            val resolved = resolveLocationCoordinates(query)
            if (resolved != null) {
                onResult(resolved.first, resolved.second, resolved.third)
            } else {
                val res = locationHelper.searchPlace(query, userLocation.value)
                if (res != null) {
                    onResult(res.first, res.second.first, res.second.second)
                }
            }
        }
    }

    fun reverseGeocode(lat: Double, lng: Double, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val addr = locationHelper.getAddressFromCoordinates(lat, lng)
            onResult(addr)
        }
    }

    fun refreshBriefing() {
        viewModelScope.launch {
            _isBriefingLoading.value = true
            try {
                _dailyBriefing.value = GeminiTaskHelper.generateDailyBriefing(allTasks.value, userName.value)
            } finally {
                _isBriefingLoading.value = false
            }
        }
    }

    /**
     * Integrates Gemini API to suggest optimal scheduling times based on task list and commitments.
     */
    fun suggestOptimalSchedule(
        taskTitle: String,
        taskPriority: String = "Medium",
        existingCommitments: String? = null,
        onResult: ((com.example.gemini.ScheduleSuggestion) -> Unit)? = null
    ) {
        if (taskTitle.isBlank()) return
        viewModelScope.launch {
            isSuggestingSchedule.value = true
            try {
                val suggestion = GeminiTaskHelper.suggestOptimalSchedule(
                    taskTitle = taskTitle,
                    taskPriority = taskPriority,
                    existingTasks = allTasks.value,
                    existingCommitments = existingCommitments
                )
                scheduleSuggestionState.value = suggestion
                onResult?.invoke(suggestion)
            } finally {
                isSuggestingSchedule.value = false
            }
        }
    }

    private fun isDueToday(timestamp: Long?): Boolean {
        if (timestamp == null) return false
        val taskCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()
        return taskCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
               taskCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Sends a natural language task description to the Gemini API and parses the response
     * to extract structured scheduling data like time, category, due date, and priority.
     */
    suspend fun extractStructuredSchedulingData(taskDescription: String): com.example.gemini.StructuredSchedulingData {
        return GeminiTaskHelper.extractStructuredSchedulingData(taskDescription)
    }

    /**
     * Uses the Gemini AI API to analyze a user's task list and suggest an optimized daily schedule based on priority.
     */
    fun analyzeTaskListAndSuggestOptimizedDailySchedule(
        tasks: List<Task> = allTasks.value,
        onResult: ((com.example.gemini.OptimizedDailySchedule) -> Unit)? = null
    ) {
        viewModelScope.launch {
            isGeneratingDailySchedule.value = true
            try {
                val result = GeminiTaskHelper.analyzeTaskListAndSuggestOptimizedDailySchedule(tasks)
                optimizedDailyScheduleState.value = result
                onResult?.invoke(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating optimized daily schedule from task list", e)
            } finally {
                isGeneratingDailySchedule.value = false
            }
        }
    }

    private val activeProximityTriggeredTaskIds = mutableSetOf<Int>()

    /**
     * Actively evaluates proximity for all location-based pending tasks against the current location.
     * Guarantees that even if Google Play Services geofence broadcast is delayed or sleeping,
     * the user gets notified immediately when entering their Home, Work, or venue geofence.
     */
    fun evaluateProximityAlerts(location: Location) {
        viewModelScope.launch {
            try {
                val pendingWithLoc = db.taskDao().getAllTasksList().filter {
                    !it.isDone && it.latitude != null && it.longitude != null &&
                    (it.latitude != 0.0 || it.longitude != 0.0)
                }

                for (task in pendingWithLoc) {
                    val distance = LocationHelper.calculateDistance(
                        location.latitude,
                        location.longitude,
                        task.latitude!!,
                        task.longitude!!
                    )
                    val isInside = distance <= task.geofenceRadius
                    val isArrival = !task.safeTriggerDirection.equals("DEPARTURE", ignoreCase = true)

                    if (isArrival) {
                        if (isInside) {
                            if (!activeProximityTriggeredTaskIds.contains(task.id)) {
                                activeProximityTriggeredTaskIds.add(task.id)
                                triggerProximityNotification(task, isArrival = true, distance = distance)
                            }
                        } else {
                            activeProximityTriggeredTaskIds.remove(task.id)
                        }
                    } else {
                        // Departure
                        if (!isInside) {
                            if (!activeProximityTriggeredTaskIds.contains(task.id)) {
                                activeProximityTriggeredTaskIds.add(task.id)
                                triggerProximityNotification(task, isArrival = false, distance = distance)
                            }
                        } else {
                            activeProximityTriggeredTaskIds.remove(task.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "evaluateProximityAlerts error", e)
            }
        }
    }

    private fun checkImmediateProximityOnTaskAdded(task: Task) {
        if (task.isDone || task.latitude == null || task.longitude == null) return
        viewModelScope.launch {
            val cur = userLocation.value ?: locationHelper.getCurrentLocation()
            if (cur != null) {
                val distance = LocationHelper.calculateDistance(
                    cur.latitude,
                    cur.longitude,
                    task.latitude!!,
                    task.longitude!!
                )
                val isArrival = !task.safeTriggerDirection.equals("DEPARTURE", ignoreCase = true)
                if (isArrival && distance <= task.geofenceRadius) {
                    activeProximityTriggeredTaskIds.add(task.id)
                    triggerProximityNotification(task, isArrival = true, distance = distance)
                }
            }
        }
    }

    private fun triggerProximityNotification(task: Task, isArrival: Boolean, distance: Float) {
        val locName = task.locationName ?: "your destination"
        val title = if (isArrival) "📍 Arrived at $locName" else "🛫 Leaving $locName"
        val body = if (isArrival) {
            "You are right here (${LocationHelper.formatDistance(distance)})! Time for: ${task.safeTitle}"
        } else {
            "Before you leave $locName: ${task.safeTitle}"
        }

        sendProximityNotification(task.id, title, body, task.safeReminderTone)

        val speech = if (isArrival) {
            "You're at $locName! Don't forget to ${task.safeTitle}."
        } else {
            "Before you leave $locName, remember to ${task.safeTitle}."
        }
        _cobbySpeech.value = speech
        speakText(speech)
    }

    fun sendProximityNotification(taskId: Int, title: String, content: String, reminderTone: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    GeofenceBroadcastReceiver.CHANNEL_ID,
                    GeofenceBroadcastReceiver.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Location & Proximity Task Reminders"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                taskId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = when (reminderTone) {
                "URGENT_ALARM" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                "GENTLE_NOTIF" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                "PHONE_RINGTONE" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                else -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            }

            val notification = NotificationCompat.Builder(context, GeofenceBroadcastReceiver.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 350, 200, 350))
                .build()

            notificationManager.notify(taskId + 30000, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending proximity notification", e)
        }
    }

    /**
     * Self-healing: Repairs any saved frequent locations or tasks that had 0.0, 0.0
     * or missing coordinates, ensuring geofences register and triggers fire reliably.
     */
    private fun healSavedLocationsAndTasks() {
        viewModelScope.launch {
            try {
                val cur = locationHelper.getCurrentLocation()
                if (cur != null && userLocation.value == null) {
                    userLocation.value = cur
                }

                // Heal saved frequent places
                val savedList = db.savedLocationDao().getAllSavedLocationsList()
                for (saved in savedList) {
                    if (saved.latitude == 0.0 && saved.longitude == 0.0) {
                        val resolved = resolveLocationCoordinates(saved.address.ifBlank { saved.name }, fallbackToCurrentLocation = true)
                        if (resolved != null && (resolved.second != 0.0 || resolved.third != 0.0)) {
                            db.savedLocationDao().updateSavedLocation(
                                saved.copy(
                                    latitude = resolved.second,
                                    longitude = resolved.third,
                                    address = saved.address.ifBlank { resolved.first }
                                )
                            )
                        }
                    }
                }

                // Heal pending tasks with missing/zero coordinates
                val tasks = db.taskDao().getAllTasksList()
                for (task in tasks) {
                    if (!task.locationName.isNullOrBlank() && (task.latitude == null || task.latitude == 0.0 || task.longitude == null || task.longitude == 0.0)) {
                        val resolved = resolveLocationCoordinates(task.locationName, fallbackToCurrentLocation = true)
                        if (resolved != null && (resolved.second != 0.0 || resolved.third != 0.0)) {
                            val healed = task.copy(
                                latitude = resolved.second,
                                longitude = resolved.third
                            )
                            db.taskDao().updateTask(healed)
                            if (!healed.isDone) {
                                geofenceManager.registerTaskGeofence(healed)
                            }
                        }
                    }
                }

                // Check proximity with current location
                userLocation.value?.let { evaluateProximityAlerts(it) }
            } catch (e: Exception) {
                Log.w(TAG, "healSavedLocationsAndTasks warning", e)
            }
        }
    }
}
