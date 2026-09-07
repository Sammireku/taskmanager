package com.example.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object GeminiTaskHelper {
    private const val TAG = "GeminiTaskHelper"

    /**
     * Parses free-form text into structured task fields.
     */
    suspend fun parseTaskFromNaturalLanguage(prompt: String): ParsedTaskData = withContext(Dispatchers.IO) {
        val fallback = extractFallbackTaskData(prompt)
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext fallback
        }

        val systemInstruction = "You are a smart task assistant. Extract task details from the user's prompt. " +
            "CRITICAL FOR PRIORITY: Look for priority indicators like 'urgent', 'asap', 'critical', 'high priority', 'p0', 'p1', 'important', 'top priority', 'emergency' -> set priority 'High'. " +
            "Look for 'low priority', 'whenever', 'minor', 'p3' -> set priority 'Low'. Otherwise default priority to 'Medium'. " +
            "CRITICAL FOR TRIGGER DIRECTION: Set 'DEPARTURE' if user mentions leaving/departing/exit, otherwise 'ARRIVAL'. " +
            "Return valid JSON only with keys: title (String), description (String or null), category (String e.g. Work, Personal, Shopping, Health, Errands, Study), priority (String: High, Medium, or Low), minutesFromNow (Long or null indicating when this is due from now in minutes), locationName (String or null for place name), subtasks (Array of strings, empty array if none), triggerDirection (String: 'DEPARTURE' or 'ARRIVAL')."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = executeGenerateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!rawText.isNullOrBlank()) {
                val cleanJson = rawText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val parsed = RetrofitClient.jsonInstance.decodeFromString<ParsedTaskData>(cleanJson)
                // Post-process with local rule engine to ensure priority and trigger indicators are never missed
                parsed.copy(
                    priority = if (fallback.priority == "High") "High" else if (fallback.priority == "Low" && parsed.priority == "Medium") "Low" else parsed.priority,
                    triggerDirection = if (fallback.triggerDirection == "DEPARTURE") "DEPARTURE" else parsed.triggerDirection,
                    category = if (parsed.category.isBlank() || parsed.category == "General") fallback.category else parsed.category,
                    locationName = if (parsed.locationName.isNullOrBlank()) fallback.locationName else parsed.locationName,
                    minutesFromNow = parsed.minutesFromNow ?: fallback.minutesFromNow
                )
            } else {
                fallback
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini API HTTP ${e.code()}: $errorBody", e)
            fallback
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse task with Gemini, using local rule engine", e)
            fallback
        }
    }

    /**
     * Deterministic local rule engine for parsing natural language prompts offline or as a fallback.
     */
    fun extractFallbackTaskData(prompt: String): ParsedTaskData {
        val lower = prompt.lowercase(java.util.Locale.ROOT)

        // Priority extraction
        val priority = when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("high priority") ||
            lower.contains("critical") || lower.contains("p0") || lower.contains("p1") ||
            lower.contains("important") || lower.contains("emergency") || lower.contains("top priority") -> "High"
            lower.contains("low priority") || lower.contains("whenever") || lower.contains("minor") ||
            lower.contains("p3") || lower.contains("low") -> "Low"
            else -> "Medium"
        }

        // Trigger direction extraction
        val triggerDirection = when {
            lower.contains("leave") || lower.contains("leaving") || lower.contains("depart") ||
            lower.contains("departing") || lower.contains("when i leave") || lower.contains("exit") -> "DEPARTURE"
            else -> "ARRIVAL"
        }

        // Category extraction
        val category = when {
            lower.contains("meeting") || lower.contains("presentation") || lower.contains("report") ||
            lower.contains("email") || lower.contains("slide") || lower.contains("office") || lower.contains("work") -> "Work"
            lower.contains("buy") || lower.contains("purchase") || lower.contains("groceries") ||
            lower.contains("store") || lower.contains("mall") || lower.contains("shop") -> "Shopping"
            lower.contains("doctor") || lower.contains("gym") || lower.contains("workout") ||
            lower.contains("medicine") || lower.contains("pharmacy") || lower.contains("clinic") -> "Health"
            lower.contains("pick up") || lower.contains("drop off") || lower.contains("dry clean") ||
            lower.contains("bank") || lower.contains("gas") || lower.contains("errand") -> "Errands"
            lower.contains("study") || lower.contains("exam") || lower.contains("homework") -> "Study"
            else -> "Personal"
        }

        // Minutes from now extraction
        val minutesFromNow: Long? = when {
            lower.contains("in 15 min") || lower.contains("in 15 mins") || lower.contains("in 15 minutes") -> 15L
            lower.contains("in 30 min") || lower.contains("in 30 mins") || lower.contains("in 30 minutes") -> 30L
            lower.contains("in 1 hour") || lower.contains("in an hour") || lower.contains("in 1 hr") -> 60L
            lower.contains("in 2 hours") || lower.contains("in 2 hrs") -> 120L
            lower.contains("tomorrow") -> 1440L
            else -> null
        }

        // Location extraction simple heuristic
        val locationName: String? = when {
            lower.contains("at office") || lower.contains("from office") -> "Office"
            lower.contains("at home") -> "Home"
            lower.contains("at gym") -> "Gym"
            lower.contains("at store") || lower.contains("at supermarket") -> "Supermarket"
            lower.contains("at mall") -> "Shopping Mall"
            else -> null
        }

        // Clean title by removing obvious keyword prefixes if necessary
        var cleanTitle = prompt.trim()
        if (cleanTitle.length > 50) {
            cleanTitle = cleanTitle.take(50) + "..."
        }

        return ParsedTaskData(
            title = cleanTitle.ifBlank { "New Task" },
            category = category,
            priority = priority,
            minutesFromNow = minutesFromNow,
            locationName = locationName,
            triggerDirection = triggerDirection
        )
    }

    /**
     * Sends a natural language task description to the Gemini API and parses
     * the response to extract structured scheduling data like time, category,
     * priority, and calculated due date timestamp.
     */
    suspend fun extractStructuredSchedulingData(taskDescription: String): StructuredSchedulingData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fallback = extractFallbackSchedulingData(taskDescription, now)
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext fallback
        }

        val systemInstruction = "You are a smart scheduling assistant. " +
            "Analyze the given natural language task description and extract structured scheduling data. " +
            "Current timestamp in epoch milliseconds is $now. " +
            "Return valid JSON only with keys: " +
            "title (String: clean, concise summary of the task), " +
            "time (String or null: human-readable extracted time or deadline, e.g. 'Today at 5:00 PM', 'Tomorrow at 10:00 AM', 'In 30 minutes', or null if not specified), " +
            "category (String: Work, Personal, Shopping, Health, Errands, Study, or General), " +
            "dueDateMillis (Long or null: epoch milliseconds calculated from the user's requested time/date relative to now, or null if none), " +
            "priority (String: 'High' if urgent/asap/important/critical, 'Low' if low/minor/whenever, otherwise 'Medium'), " +
            "description (String or null: additional details)."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = taskDescription)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = executeGenerateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!rawText.isNullOrBlank()) {
                val cleanJson = rawText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                val parsed = RetrofitClient.jsonInstance.decodeFromString<StructuredSchedulingData>(cleanJson)
                parsed.copy(
                    priority = if (fallback.priority == "High") "High" else if (fallback.priority == "Low" && parsed.priority == "Medium") "Low" else parsed.priority,
                    category = if (parsed.category.isBlank() || parsed.category == "General") fallback.category else parsed.category,
                    time = if (parsed.time.isNullOrBlank()) fallback.time else parsed.time,
                    dueDateMillis = parsed.dueDateMillis ?: fallback.dueDateMillis
                )
            } else {
                fallback
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini API HTTP ${e.code()}: $errorBody", e)
            fallback
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract structured scheduling data with Gemini, using fallback", e)
            fallback
        }
    }

    /**
     * Local deterministic fallback for extracting structured scheduling data.
     */
    fun extractFallbackSchedulingData(taskDescription: String, currentEpochMs: Long = System.currentTimeMillis()): StructuredSchedulingData {
        val parsed = extractFallbackTaskData(taskDescription)
        val calculatedDue = parsed.minutesFromNow?.let { currentEpochMs + (it * 60 * 1000) }
        val timeString = parsed.minutesFromNow?.let {
            val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            sdf.format(java.util.Date(currentEpochMs + (it * 60 * 1000)))
        }
        return StructuredSchedulingData(
            title = parsed.title,
            time = timeString,
            category = parsed.category,
            dueDateMillis = calculatedDue,
            priority = parsed.priority,
            description = parsed.description
        )
    }

    /**
     * Breaks down a task into 3-5 actionable subtasks.
     */
    suspend fun generateSubtasks(taskTitle: String): List<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return@withContext emptyList()

        val prompt = "Break down this task into 3 to 5 actionable subtasks: \"$taskTitle\". Return JSON array of strings only, e.g. [\"Subtask 1\", \"Subtask 2\"]."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4f
            )
        )

        try {
            val response = executeGenerateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!rawText.isNullOrBlank()) {
                val cleanJson = rawText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                RetrofitClient.jsonInstance.decodeFromString<List<String>>(cleanJson)
            } else {
                emptyList()
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini API HTTP ${e.code()}: $errorBody", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate subtasks", e)
            emptyList()
        }
    }

    /**
     * Generates a 1-2 sentence morning or daily focus briefing based on task list.
     */
    suspend fun generateDailyBriefing(tasks: List<Task>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            val pending = tasks.count { !it.isCompleted }
            return@withContext if (pending == 0) "All caught up! Have a wonderful and relaxing day." else "You have $pending active tasks today. Focus on what matters most!"
        }

        val pendingTasks = tasks.filter { !it.isCompleted }
        if (pendingTasks.isEmpty()) {
            return@withContext "You're all clear! No pending tasks right now. Great job staying on top of your day."
        }

        val taskTitles = pendingTasks.take(6).joinToString(", ") { "${it.title} (${it.priority} priority)" }
        val prompt = "Based on these pending tasks: $taskTitles. Write a short, motivating, 1 to 2 sentence focus recommendation for today. Keep it friendly and concise without greeting boilerplate."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.7f
            )
        )

        try {
            val response = executeGenerateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: "Stay focused and tackle your highest priority tasks first today!"
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini API HTTP ${e.code()}: $errorBody", e)
            "Stay focused and tackle your highest priority tasks first today!"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily briefing", e)
            "Stay focused and tackle your highest priority tasks first today!"
        }
    }

    /**
     * Suggests optimal scheduling time for a task based on user's task list and existing commitments using Gemini API.
     */
    suspend fun suggestOptimalSchedule(
        taskTitle: String,
        taskPriority: String = "Medium",
        existingTasks: List<Task> = emptyList(),
        existingCommitments: String? = null
    ): ScheduleSuggestion = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext ScheduleSuggestion(
                timeSlotText = "Today at 3:00 PM",
                suggestedMinutesFromNow = 180,
                reasoning = "Default afternoon focus block for priority $taskPriority tasks."
            )
        }

        val pending = existingTasks.filter { !it.isCompleted }.take(8)
        val existingTasksSummary = if (pending.isNotEmpty()) {
            pending.joinToString("; ") {
                val due = if (it.dueDate != null) "due in ${java.util.concurrent.TimeUnit.MILLISECONDS.toHours(it.dueDate - System.currentTimeMillis()).coerceAtLeast(0)}h" else "no fixed due time"
                "\"${it.title}\" (${it.priority} priority, $due)"
            }
        } else {
            "No existing pending tasks."
        }

        val commitmentsSummary = if (!existingCommitments.isNullOrBlank()) "User commitments: $existingCommitments" else "No additional calendar commitments reported."

        val systemInstruction = "You are an AI Smart Task Scheduler. Recommend the optimal time slot to work on the target task given the user's existing task load and commitments. Return valid JSON only with keys: timeSlotText (String, e.g. 'Today at 4:30 PM' or 'Tomorrow at 10:00 AM'), suggestedMinutesFromNow (Long indicating minutes from now when to schedule, e.g. 120 or 1440), and reasoning (String, 1-2 sentence concise explanation of why this slot is optimal)."

        val prompt = "Target Task: \"$taskTitle\" (Priority: $taskPriority).\nExisting Tasks: $existingTasksSummary.\n$commitmentsSummary.\nSuggest the best scheduling time."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = executeGenerateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!rawText.isNullOrBlank()) {
                val cleanJson = rawText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                RetrofitClient.jsonInstance.decodeFromString<ScheduleSuggestion>(cleanJson)
            } else {
                ScheduleSuggestion(
                    timeSlotText = "Today at 3:00 PM",
                    suggestedMinutesFromNow = 180,
                    reasoning = "Recommended focus time slot based on current task list."
                )
            }
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Gemini API HTTP ${e.code()}: $errorBody", e)
            ScheduleSuggestion(
                timeSlotText = "Today at 3:00 PM",
                suggestedMinutesFromNow = 180,
                reasoning = "Recommended focus time slot based on current task list."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get optimal schedule from Gemini", e)
            ScheduleSuggestion(
                timeSlotText = "Today at 3:00 PM",
                suggestedMinutesFromNow = 180,
                reasoning = "Recommended focus time slot based on current task list."
            )
        }
    }

    private suspend fun executeGenerateContent(apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
        return try {
            RetrofitClient.service.generateContent(apiKey, request)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                Log.w(TAG, "gemini-3.5-flash endpoint returned 404, falling back to gemini-flash-latest")
                RetrofitClient.service.generateContentWithModel("gemini-flash-latest", apiKey, request)
            } else {
                throw e
            }
        }
    }
}
