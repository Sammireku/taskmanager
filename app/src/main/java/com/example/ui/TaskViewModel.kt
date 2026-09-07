package com.example.ui

import android.content.Context
import android.location.Location
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
    OFFLINE_ROOM("Offline (Room DB)"),
    SYNCING("Syncing..."),
    SYNCED("Synced")
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
                // Simulate/trigger bidirectional sync between Room offline cache and backend
                delay(1200)
                _syncStatus.value = SyncStatus.SYNCED
                delay(2500)
                _syncStatus.value = SyncStatus.OFFLINE_ROOM
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.OFFLINE_ROOM
            }
        }
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
        _cobbySpeech.value = quotes.random()
        _cobbyMood.value = CobbyMood.TALKING
        viewModelScope.launch {
            delay(3500)
            if (_cobbyMood.value == CobbyMood.TALKING) {
                _cobbyMood.value = CobbyMood.IDLE
            }
        }
    }

    fun reactToTaskAdded(task: Task) {
        _cobbySpeech.value = "Awesome! Scheduled \"${task.safeTitle}\" with WorkManager reminders! 🚀"
        _cobbyMood.value = CobbyMood.EXCITED
        viewModelScope.launch {
            delay(3500)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    fun reactToTaskCompleted(task: Task) {
        _cobbySpeech.value = "Woohoo! \"${task.safeTitle}\" completed! High five! 🎉"
        _cobbyMood.value = CobbyMood.CELEBRATING
        viewModelScope.launch {
            delay(4500)
            _cobbyMood.value = CobbyMood.IDLE
        }
    }

    fun reactToTaskDeleted(task: Task) {
        _cobbySpeech.value = "Removed \"${task.safeTitle}\". Focused and clear!"
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
                reactToTaskCompleted(updated)
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
            workScheduler.cancelDueDateReminder(task.id)
            alarmScheduler.cancelTaskAlarm(task.id)
            geofenceManager.removeTaskGeofence(task.id)
            db.taskDao().deleteTask(task)
            TaskWidgetProvider.updateAllWidgets(context)
            reactToTaskDeleted(task)
        }
    }

    fun restoreLastDeletedTask() {
        viewModelScope.launch {
            lastDeletedTask?.let {
                val id = db.taskDao().insertTask(it)
                val restoredTask = it.copy(id = id.toInt())
                workScheduler.scheduleDueDateReminder(restoredTask)
                alarmScheduler.scheduleTaskAlarm(restoredTask)
                if (it.latitude != null && it.longitude != null && !it.isCompleted) {
                    geofenceManager.registerTaskGeofence(restoredTask)
                }
                lastDeletedTask = null
                TaskWidgetProvider.updateAllWidgets(context)
                reactToTaskAdded(restoredTask)
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
