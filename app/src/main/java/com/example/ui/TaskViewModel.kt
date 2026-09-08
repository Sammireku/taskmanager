package com.example.ui

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SubTask
import com.example.data.Task
import com.example.gemini.GeminiTaskHelper
import com.example.gemini.RetrofitClient
import com.example.location.ErrandCluster
import com.example.location.GeofenceManager
import com.example.location.LocationHelper
import com.example.location.ProximityTaskInfo
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

import com.example.places.PlacesService
import com.example.places.PlaceSuggestion
import com.example.places.PlaceDetails
import com.example.places.TaskPlaceInfo
import com.example.widget.TaskWidgetProvider
import com.example.notification.TaskAlarmScheduler
import com.example.work.TaskWorkScheduler
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.delay

enum class CobbyMood {
    IDLE,
    TALKING,
    CELEBRATING,
    EXCITED,
    THINKING
}

enum class SyncStatus(val label: String) {
    OFFLINE_ROOM("Offline Local Persistence (Room DB)"),
    SYNCING("Refreshing Local DB..."),
    SYNCED("Local DB Active")
}

class TaskViewModel(
    private val db: AppDatabase,
    private val context: Context
) : ViewModel() {

    val locationHelper = LocationHelper(context.applicationContext)
    val geofenceManager = GeofenceManager(context.applicationContext)
    val placesService = PlacesService(context.applicationContext)
    val alarmScheduler = TaskAlarmScheduler(context.applicationContext)
    val workScheduler = TaskWorkScheduler(context.applicationContext)
    val firestoreRepository = com.example.data.FirestoreRepository()

    private var tts: TextToSpeech? = null
    var isTtsEnabled = MutableStateFlow(true)
        private set

    private var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
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

    fun speakText(text: String) {
        if (isTtsEnabled.value && text.isNotBlank()) {
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

    private val _cobbySpeech = MutableStateFlow("Hey there! I'm Cobby. Tap me or hit the '+' button to log your tasks!")
    val cobbySpeech: StateFlow<String> = _cobbySpeech.asStateFlow()

    // Dynamic color & theme switching state
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(true)
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    // Swipe to delete task toggle
    private val _swipeToDeleteEnabled = MutableStateFlow(true)
    val swipeToDeleteEnabled: StateFlow<Boolean> = _swipeToDeleteEnabled.asStateFlow()

    // Offline-first Room vs Backend sync status indicator
    private val _syncStatus = MutableStateFlow(SyncStatus.OFFLINE_ROOM)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val placeSuggestions = MutableStateFlow<List<PlaceSuggestion>>(emptyList())
    val isSearchingPlaces = MutableStateFlow(false)
    val selectedPlaceDetails = MutableStateFlow<PlaceDetails?>(null)

    val scheduleSuggestionState = MutableStateFlow<com.example.gemini.ScheduleSuggestion?>(null)
    val isSuggestingSchedule = MutableStateFlow(false)

    val allTasks: StateFlow<List<Task>> = db.taskDao().getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
                "Done" -> task.isCompleted
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
                tasks.filter { !it.isCompleted && (it.dueDate ?: 0) > System.currentTimeMillis() }
                    .forEach { alarmScheduler.scheduleTaskAlarm(it) }
            }
        }

        // Fetch location on startup
        refreshLocation()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun toggleDynamicColor() {
        _dynamicColorEnabled.value = !_dynamicColorEnabled.value
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

    fun triggerBackendSync() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            try {
                val localTasks = db.taskDao().getAllTasksList()
                val remoteTasks = firestoreRepository.syncTasks(localTasks)
                for (r in remoteTasks) {
                    db.taskDao().insertTask(r)
                }
                _syncStatus.value = SyncStatus.SYNCED
                val msg = "Firestore synchronized! ${remoteTasks.size} tasks in sync with cloud."
                _cobbySpeech.value = msg
                speakText(msg)
                delay(2500)
                _syncStatus.value = SyncStatus.OFFLINE_ROOM
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.OFFLINE_ROOM
                val msg = "Sync attempt saved locally: ${e.message ?: "Offline"}"
                _cobbySpeech.value = msg
            }
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
                put("completionStatus", t.completionStatus)
                put("isHabit", t.isHabit)
                put("habitFrequency", t.habitFrequency.orEmpty())
                put("isCompleted", t.isDone)
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
                val importedTask = Task(
                    title = obj.optString("title", "Imported Task"),
                    description = obj.optString("description").ifBlank { null },
                    priority = obj.optString("priority", "Medium"),
                    dueDate = if (obj.has("dueDate") && obj.getLong("dueDate") > 0) obj.getLong("dueDate") else null,
                    completionStatus = obj.optString("completionStatus", "PENDING"),
                    status = obj.optString("completionStatus", "PENDING"),
                    isHabit = obj.optBoolean("isHabit", false),
                    habitFrequency = obj.optString("habitFrequency").ifBlank { null },
                    isCompleted = obj.optBoolean("isCompleted", false),
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
            }
        }
    }

    fun getCurrentLocation(onResult: (Location?) -> Unit) {
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                userLocation.value = loc
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
        val quotes = listOf(
            "Ready to conquer your to-do list? I'm right here with you!",
            "Tasks stored safely offline in Room Database! ⚡",
            "WorkManager is standing by to alert you before deadlines!",
            "Pro-tip: Tap the '+' button to schedule a high-priority task!",
            "Small daily progress equals massive long-term results! 💪",
            "You're doing great! Keep knocking out those goals!"
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
        val msg = "Awesome! Scheduled \"${task.safeTitle}\" with WorkManager reminders! 🚀"
        _cobbySpeech.value = msg
        speakText(msg)
        _cobbyMood.value = CobbyMood.EXCITED
        viewModelScope.launch {
            delay(3500)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    fun reactToTaskCompleted(task: Task) {
        val msg = "Woohoo! \"${task.safeTitle}\" completed! High five! 🎉"
        _cobbySpeech.value = msg
        speakText(msg)
        _cobbyMood.value = CobbyMood.CELEBRATING
        viewModelScope.launch {
            delay(4500)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    fun reactToTaskDeleted(task: Task) {
        val msg = "Removed \"${task.safeTitle}\". Focused and clear!"
        _cobbySpeech.value = msg
        speakText(msg)
        viewModelScope.launch {
            delay(2500)
            _cobbySpeech.value = "Hey! What should we tackle next?"
        }
    }

    fun createTask(
        title: String,
        description: String? = null,
        priority: String = "Medium",
        dueDate: Long? = null,
        completionStatus: String = "PENDING",
        category: String = "General"
    ) {
        val isCompleted = completionStatus.equals("COMPLETED", ignoreCase = true)
        val task = Task(
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate,
            completionStatus = completionStatus,
            status = completionStatus,
            isCompleted = isCompleted,
            category = category
        )
        addTask(task)
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            val id = db.taskDao().insertTask(task)
            val savedTask = task.copy(id = id.toInt())
            // WorkManager local push notification trigger for upcoming due dates
            workScheduler.scheduleDueDateReminder(savedTask)
            alarmScheduler.scheduleTaskAlarm(savedTask)
            if (task.latitude != null && task.longitude != null && !task.isCompleted) {
                geofenceManager.registerTaskGeofence(savedTask)
            }
            TaskWidgetProvider.updateAllWidgets(context)
            reactToTaskAdded(savedTask)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            val isDone = task.isCompleted || task.completionStatus.equals("COMPLETED", ignoreCase = true)
            val normalized = task.copy(
                completionStatus = if (isDone) "COMPLETED" else task.completionStatus,
                status = if (isDone) "COMPLETED" else task.status,
                isCompleted = isDone
            )
            db.taskDao().updateTask(normalized)
            if (isDone) {
                workScheduler.cancelDueDateReminder(normalized.id)
                alarmScheduler.cancelTaskAlarm(normalized.id)
                geofenceManager.removeTaskGeofence(normalized.id)
            } else {
                workScheduler.scheduleDueDateReminder(normalized)
                alarmScheduler.scheduleTaskAlarm(normalized)
                if (normalized.latitude != null && normalized.longitude != null) {
                    geofenceManager.registerTaskGeofence(normalized)
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
                isCompleted = nowCompleted,
                completionStatus = if (nowCompleted) "COMPLETED" else "PENDING",
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
                        isCompleted = false,
                        completionStatus = "PENDING",
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
            firestoreRepository.saveTask(task.copy(isDeleted = true, deletedAt = timestamp))
            TaskWidgetProvider.updateAllWidgets(context)
            val msg = "Moved \"${task.safeTitle}\" to Trash! (30-day retention buffer)"
            _cobbySpeech.value = msg
            speakText(msg)
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            db.taskDao().restoreTask(task.id)
            firestoreRepository.saveTask(task.copy(isDeleted = false, deletedAt = null))
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
     * Natural Language Task Parsing with Gemini & auto location geocoding.
     */
    fun parseAndAddTask(inputText: String, onComplete: ((Task) -> Unit)? = null) {
        if (inputText.isBlank()) return
        viewModelScope.launch {
            _isAiParsing.value = true
            try {
                val parsed = GeminiTaskHelper.parseTaskFromNaturalLanguage(inputText)
                val calculatedDueDate = parsed.minutesFromNow?.let {
                    System.currentTimeMillis() + (it * 60 * 1000)
                }

                val subtasksList = parsed.subtasks.map { SubTask(title = it) }
                val subtasksJson = if (subtasksList.isNotEmpty()) {
                    RetrofitClient.jsonInstance.encodeToString(subtasksList)
                } else null

                // Auto-resolve coordinates if a location name was mentioned
                var lat: Double? = null
                var lng: Double? = null
                var resolvedLocationName = parsed.locationName

                if (!parsed.locationName.isNullOrBlank()) {
                    val placeResult = locationHelper.searchPlace(parsed.locationName)
                    if (placeResult != null) {
                        resolvedLocationName = placeResult.first
                        lat = placeResult.second.first
                        lng = placeResult.second.second
                    }
                }

                val task = Task(
                    title = parsed.title.ifBlank { inputText },
                    description = parsed.description,
                    dueDate = calculatedDueDate,
                    category = parsed.category,
                    priority = parsed.priority,
                    locationName = resolvedLocationName,
                    latitude = lat,
                    longitude = lng,
                    geofenceRadius = 150f,
                    triggerDirection = parsed.triggerDirection,
                    subtasksJson = subtasksJson
                )
                val id = db.taskDao().insertTask(task)
                val savedTask = task.copy(id = id.toInt())
                alarmScheduler.scheduleTaskAlarm(savedTask)
                if (lat != null && lng != null) {
                    geofenceManager.registerTaskGeofence(savedTask)
                }
                TaskWidgetProvider.updateAllWidgets(context)
                onComplete?.invoke(savedTask)
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

    /**
     * Search Places using Google Places SDK Autocomplete with intelligent Geocoder fallback
     */
    fun searchPlacesAutocomplete(query: String) {
        if (query.isBlank()) {
            placeSuggestions.value = emptyList()
            isSearchingPlaces.value = false
            return
        }
        viewModelScope.launch {
            isSearchingPlaces.value = true
            try {
                val suggestions = placesService.getAutocompletePredictions(query)
                if (suggestions.isNotEmpty()) {
                    placeSuggestions.value = suggestions
                } else {
                    val geoList = locationHelper.searchPlacesList(query, maxResults = 5)
                    placeSuggestions.value = geoList.map { item ->
                        PlaceSuggestion(
                            placeId = "geo_${item.second.first}_${item.second.second}",
                            primaryText = item.first,
                            secondaryText = "Lat: ${String.format(java.util.Locale.US, "%.4f", item.second.first)}, Lng: ${String.format(java.util.Locale.US, "%.4f", item.second.second)}",
                            fullText = item.first
                        )
                    }
                }
            } catch (e: Exception) {
                try {
                    val geoList = locationHelper.searchPlacesList(query, maxResults = 5)
                    placeSuggestions.value = geoList.map { item ->
                        PlaceSuggestion(
                            placeId = "geo_${item.second.first}_${item.second.second}",
                            primaryText = item.first,
                            secondaryText = "Lat: ${String.format(java.util.Locale.US, "%.4f", item.second.first)}, Lng: ${String.format(java.util.Locale.US, "%.4f", item.second.second)}",
                            fullText = item.first
                        )
                    }
                } catch (_: Exception) {
                    placeSuggestions.value = emptyList()
                }
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

    fun searchLocation(query: String, onResult: (name: String, lat: Double, lng: Double) -> Unit) {
        viewModelScope.launch {
            val res = locationHelper.searchPlace(query)
            if (res != null) {
                onResult(res.first, res.second.first, res.second.second)
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
                _dailyBriefing.value = GeminiTaskHelper.generateDailyBriefing(allTasks.value)
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
}
