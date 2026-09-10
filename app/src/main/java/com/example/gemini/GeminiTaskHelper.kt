package com.example.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonPrimitive

object GeminiTaskHelper {
    private const val TAG = "GeminiTaskHelper"

    private fun safeLogE(tag: String, msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        } catch (_: Throwable) {
            println("E/$tag: $msg ${t?.message ?: ""}")
        }
    }

    private fun safeLogW(tag: String, msg: String, t: Throwable? = null) {
        try {
            if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        } catch (_: Throwable) {
            println("W/$tag: $msg ${t?.message ?: ""}")
        }
    }

    private val conversationalExtractionSchema = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("task") {
                put("type", "STRING")
                put("description", "the core action, stripped of location/time clauses")
            }
            putJsonObject("location") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "STRING")
                        put("nullable", true)
                    }
                    putJsonObject("specificity") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add(JsonPrimitive("named_venue"))
                            add(JsonPrimitive("category"))
                            add(JsonPrimitive("relative_to_user"))
                            add(JsonPrimitive("referential"))
                            add(JsonPrimitive("none"))
                        }
                    }
                    putJsonObject("trigger") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add(JsonPrimitive("arrival"))
                            add(JsonPrimitive("departure"))
                            add(JsonPrimitive("proximity"))
                            add(JsonPrimitive("none"))
                        }
                    }
                }
            }
            putJsonObject("time") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "STRING")
                        put("nullable", true)
                    }
                    putJsonObject("type") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add(JsonPrimitive("absolute"))
                            add(JsonPrimitive("relative"))
                            add(JsonPrimitive("recurring"))
                            add(JsonPrimitive("none"))
                        }
                    }
                }
            }
            putJsonObject("confidence") {
                put("type", "STRING")
                putJsonArray("enum") {
                    add(JsonPrimitive("high"))
                    add(JsonPrimitive("medium"))
                    add(JsonPrimitive("low"))
                }
            }
            putJsonObject("ambiguous_spans") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
            putJsonObject("clarification_question") {
                put("type", "STRING")
                put("nullable", true)
            }
            putJsonObject("clarification_options") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("task"))
            add(JsonPrimitive("confidence"))
            add(JsonPrimitive("ambiguous_spans"))
        }
    }

    /**
     * Parses spoken or typed input using Gemini schema-enforced structured JSON mode,
     * dynamically feeding user_context and identifying ambiguities for conversational clarification.
     */
    suspend fun parseConversationalTask(
        utterance: String,
        savedPlaces: List<String> = emptyList(),
        recentReminders: List<Task> = emptyList(),
        userName: String? = null
    ): ConversationalTaskExtraction = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val fallback = extractFallbackConversationalTask(utterance, savedPlaces, recentReminders)

        val contextBuilder = StringBuilder()
        if (!userName.isNullOrBlank()) {
            contextBuilder.append("User's name: $userName. Address $userName naturally and politely if asking clarification questions.\n")
        }
        if (savedPlaces.isNotEmpty()) {
            contextBuilder.append("User saved places: [${savedPlaces.joinToString(", ")}]\n")
        }
        if (recentReminders.isNotEmpty()) {
            contextBuilder.append("User's recent reminders (for referential resolution like 'same place as last time'):\n")
            recentReminders.take(3).forEach { task ->
                val loc = task.locationName?.let { " at \"$it\"" } ?: ""
                val time = task.dueDate?.let { " (scheduled time)" } ?: ""
                contextBuilder.append("- \"${task.safeTitle}\"$loc$time\n")
            }
        }
        val userContextStr = contextBuilder.toString().trim()

        val systemInstruction = """
You are a natural language parser for a location-and-time-based reminder app. 
Your job is to convert a user's spoken or typed request into structured JSON. 
You do NOT resolve real-world locations or times yourself — you only extract 
and classify what the user said. A separate system will validate your output 
against real map and calendar data.

## Input
You will receive:
- `utterance`: the raw user input (may be voice-transcribed, so expect filler 
  words, missing punctuation, or ASR errors)
- `user_context` (optional): the user's saved places (e.g. "home", "gym", 
  "office" with labels only, no coordinates), and their last 1-3 reminders 
  for resolving references like "same place as last time"

## Output schema
Return ONLY valid JSON matching this exact structure:

{
  "task": string,                  // the core action, stripped of location/time clauses
  "location": {
    "query": string | null,        // the location phrase as the user said it, or null
    "specificity": "named_venue" | "category" | "relative_to_user" | "referential" | "none",
    "trigger": "arrival" | "departure" | "proximity" | "none"
  },
  "time": {
    "query": string | null,        // the time phrase as the user said it, or null
    "type": "absolute" | "relative" | "recurring" | "none"
  },
  "confidence": "high" | "medium" | "low",
  "ambiguous_spans": [string],     // phrases you were unsure how to classify; empty array if none
  "clarification_question": string | null, // friendly conversational question if unsure, e.g. "Did you mean 5:00 AM or 5:00 PM?"
  "clarification_options": [string] // 2-4 short quick-reply options, e.g. ["5:00 AM", "5:00 PM"]
}

## Classification rules

**location.specificity**
- "named_venue": a specific proper-noun place ("Trader Joe's", "Aunt Mary's house")
- "category": a type of place, not a specific one ("a pharmacy", "the gym" if not in user_context)
- "relative_to_user": resolvable only via the user's own saved places or current position ("home", "work", "here")
- "referential": refers back to a previous reminder or unstated context ("same place as before", "there")
- "none": no location mentioned

**location.trigger**
- "arrival": reminder should fire on reaching the location ("when I get to", "at")
- "departure": reminder should fire on leaving ("when I leave", "on my way out of")
- "proximity": reminder should fire near but not necessarily at the location ("near", "close to", "around")
- "none": no clear trigger, or location is present but timing/trigger is unclear — do not guess; use "none" and add the ambiguity to `ambiguous_spans`

## Critical disambiguation guidance

1. **Infinitive "to" vs. prepositional "to"**: Sentences like "remind me to buy groceries at Trader Joe's" contain "to" as an infinitive marker (part of "remind me to buy"), NOT as a location preposition. Only treat "to" as indicating a destination when it follows a verb of motion ("go to", "drive to", "walk to", "heading to", "on the way to"). Never extract a location from a bare infinitive "to X" construction.

2. **Multiple location candidates**: If the utterance contains more than one plausible location phrase (e.g., "stop by the gym on my way to the store"), extract the one tied to the actual task/trigger, and list the other as an ambiguous_span rather than silently discarding it. Set clarification_question to ask which location to trigger at.

3. **Unknown venues**: Do not attempt to validate whether a venue exists — extract it as stated. Real-world resolution happens downstream.

4. **Voice artifacts**: Ignore filler words ("um", "uh", "like") and disfluencies when extracting `task`, but do not let them affect your confidence score unless they obscure meaning.

5. **Ambiguous times (e.g. "at 5", "at 6", "tomorrow at 8")**: When a bare hour number without AM or PM is provided (e.g. "at 5"), set confidence to "low" or "medium", add the time phrase to ambiguous_spans, set clarification_question to "Did you mean 5:00 AM or 5:00 PM?", and clarification_options to ["5:00 AM", "5:00 PM"].

6. **When in doubt, lower confidence rather than guess.** A low-confidence but honest extraction is far better than a confident wrong one — this app fires real-world actions based on your output.

## Examples
Input: "remind me to buy groceries at Trader Joe's"
Output: {"task": "buy groceries", "location": {"query": "Trader Joe's", "specificity": "named_venue", "trigger": "arrival"}, "time": {"query": null, "type": "none"}, "confidence": "high", "ambiguous_spans": [], "clarification_question": null, "clarification_options": []}

Input: "remind me to grab dry cleaning near the gym on my way to work"
Output: {"task": "grab dry cleaning", "location": {"query": "the gym", "specificity": "category", "trigger": "proximity"}, "time": {"query": null, "type": "none"}, "confidence": "medium", "ambiguous_spans": ["on my way to work"], "clarification_question": "Should I set the reminder for the gym or work?", "clarification_options": ["The gym", "Work"]}

Input: "remind me to go to the barbershop at 5"
Output: {"task": "go to the barbershop", "location": {"query": "the barbershop", "specificity": "category", "trigger": "arrival"}, "time": {"query": "at 5", "type": "absolute"}, "confidence": "low", "ambiguous_spans": ["at 5 — unspecified whether morning or evening"], "clarification_question": "Did you mean 5:00 AM or 5:00 PM?", "clarification_options": ["5:00 AM", "5:00 PM"]}
        """.trimIndent()

        val promptPayload = if (userContextStr.isNotBlank()) {
            "User Context:\n$userContextStr\n\nUtterance: \"$utterance\""
        } else {
            "Utterance: \"$utterance\""
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = promptPayload)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = conversationalExtractionSchema,
                temperature = 0.1f
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
                val parsed = RetrofitClient.jsonInstance.decodeFromString<ConversationalTaskExtraction>(cleanJson)
                postProcessConversationalExtraction(parsed, utterance, recentReminders)
            } else {
                fallback
            }
        } catch (e: Exception) {
            safeLogW(TAG, "Conversational extraction via Gemini API failed, using fallback engine", e)
            fallback
        }
    }

    /**
     * Inspects parsed extraction for semantic ambiguities (e.g. bare "at 5" without AM/PM)
     * and guarantees conversational clarification questions and options are populated.
     */
    fun postProcessConversationalExtraction(
        parsed: ConversationalTaskExtraction,
        utterance: String,
        recentReminders: List<Task> = emptyList()
    ): ConversationalTaskExtraction {
        var confidence = parsed.confidence
        val ambiguousSpans = parsed.ambiguous_spans.toMutableList()
        var question = parsed.clarification_question
        var options = parsed.clarification_options

        // 1. Check for ambiguous time: bare hour without AM/PM (e.g. "at 5", "at 6:30", "at 7")
        val bareHourRegex = Regex("""\b(?:at\s+|@\s*)(\d{1,2})(?::(\d{2}))?\b(?!\s*(?:am|pm|a\.m\.|p\.m\.))""", RegexOption.IGNORE_CASE)
        val match = bareHourRegex.find(utterance)
        if (match != null) {
            val hour = match.groupValues[1].toIntOrNull() ?: 5
            val minStr = match.groupValues.getOrNull(2)?.let { if (it.isNotBlank()) ":$it" else ":00" } ?: ":00"
            confidence = "low"
            val matchedSpan = match.value.trim()
            if (!ambiguousSpans.contains(matchedSpan)) {
                ambiguousSpans.add(matchedSpan)
            }
            if (question.isNullOrBlank()) {
                question = "Did you mean $hour$minStr AM or $hour$minStr PM?"
                options = listOf("$hour$minStr AM", "$hour$minStr PM")
            }
        }

        // 2. Check for referential location like "same place as before" or "there"
        if (parsed.location?.specificity == "referential" && recentReminders.isNotEmpty()) {
            val lastLocation = recentReminders.firstOrNull { !it.locationName.isNullOrBlank() }?.locationName
            if (lastLocation != null && question.isNullOrBlank()) {
                confidence = "low"
                question = "Did you mean $lastLocation from your last reminder?"
                options = listOf(lastLocation, "Different location")
            }
        }

        return parsed.copy(
            confidence = confidence,
            ambiguous_spans = ambiguousSpans,
            clarification_question = question,
            clarification_options = options
        )
    }

    /**
     * Local deterministic fallback rule engine for conversational extraction.
     */
    fun extractFallbackConversationalTask(
        utterance: String,
        savedPlaces: List<String> = emptyList(),
        recentReminders: List<Task> = emptyList()
    ): ConversationalTaskExtraction {
        val lower = utterance.lowercase(java.util.Locale.ROOT).trim()

        // 1. Detect bare hour time without AM/PM
        val bareHourRegex = Regex("""\b(?:at\s+|@\s*)(\d{1,2})(?::(\d{2}))?\b(?!\s*(?:am|pm|a\.m\.|p\.m\.))""", RegexOption.IGNORE_CASE)
        val bareHourMatch = bareHourRegex.find(utterance)

        val explicitAmPmRegex = Regex("""\b(?:at\s+|@\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)\b""", RegexOption.IGNORE_CASE)
        val amPmMatch = explicitAmPmRegex.find(utterance)

        val timeQuery: String?
        val timeType: String
        var confidence = "high"
        val ambiguousSpans = mutableListOf<String>()
        var question: String? = null
        var options = emptyList<String>()

        if (bareHourMatch != null) {
            val hour = bareHourMatch.groupValues[1].toIntOrNull() ?: 5
            val minStr = bareHourMatch.groupValues.getOrNull(2)?.let { if (it.isNotBlank()) ":$it" else ":00" } ?: ":00"
            timeQuery = "at $hour$minStr"
            timeType = "absolute"
            confidence = "low"
            ambiguousSpans.add(bareHourMatch.value.trim())
            question = "Did you mean $hour$minStr AM or $hour$minStr PM?"
            options = listOf("$hour$minStr AM", "$hour$minStr PM")
        } else if (amPmMatch != null) {
            timeQuery = amPmMatch.value.trim()
            timeType = "absolute"
        } else if (lower.contains("tomorrow")) {
            timeQuery = "tomorrow"
            timeType = "relative"
        } else {
            timeQuery = null
            timeType = "none"
        }

        // 2. Detect location query
        var locQuery: String? = null
        var locSpecificity = "none"
        var locTrigger = "arrival"

        if (lower.contains("leave") || lower.contains("leaving") || lower.contains("when i leave")) {
            locTrigger = "departure"
        } else if (lower.contains("near") || lower.contains("close to") || lower.contains("around")) {
            locTrigger = "proximity"
        }

        // Referential checks
        if (lower.contains("same place") || lower.contains("as last time") || lower.contains("as before") || lower.contains("there")) {
            locSpecificity = "referential"
            val lastLoc = recentReminders.firstOrNull { !it.locationName.isNullOrBlank() }?.locationName
            if (lastLoc != null) {
                locQuery = lastLoc
                confidence = "low"
                ambiguousSpans.add("Referential location '$lastLoc'")
                if (question == null) {
                    question = "Did you mean $lastLoc from your last reminder?"
                    options = listOf(lastLoc, "Different location")
                }
            } else {
                locQuery = "previous place"
                confidence = "low"
                ambiguousSpans.add("Unknown referential place")
            }
        } else {
            // Check saved places
            for (saved in savedPlaces) {
                if (lower.contains(saved.lowercase(java.util.Locale.ROOT))) {
                    locQuery = saved
                    locSpecificity = if (saved.equals("Home", true) || saved.equals("Work", true) || saved.equals("Office", true)) "relative_to_user" else "named_venue"
                    break
                }
            }
            if (locQuery == null) {
                // Common location patterns - prioritize compound verb-preposition before bare preposition
                val locPatterns = listOf(
                    Regex("""\b(?:go\s+to|head\s+to|visit)\s+(?:the\s+)?([A-Za-z0-9\s'’-]+?)(?=\s+(?:at\s+\d|tomorrow|today|tonight|in\s+\d|$))""", RegexOption.IGNORE_CASE),
                    Regex("""\b(?:at|to|near|by)\s+(?:the\s+)?([A-Za-z0-9\s'’-]+?)(?=\s+(?:at\s+\d|tomorrow|today|tonight|in\s+\d|$))""", RegexOption.IGNORE_CASE)
                )
                for (pat in locPatterns) {
                    val m = pat.find(utterance)
                    if (m != null) {
                        val cand = m.groupValues[1].trim()
                        val invalidCands = setOf("buy", "get", "go", "do", "call", "see", "finish")
                        if (cand.length > 2 && !invalidCands.contains(cand.lowercase(java.util.Locale.ROOT))) {
                            locQuery = cand
                            locSpecificity = "category"
                            break
                        }
                    }
                }
            }
        }

        // Clean task action string
        var cleanedTask = utterance
            .replace(Regex("""(?i)^\s*(?:please\s+)?(?:remind\s+me\s+to|don'?t\s+forget\s+to|remember\s+to|i\s+need\s+to|task\s*:?)\s*"""), "")
            .replace(Regex("""(?i)\b(?:at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?|\d{1,2}(?::\d{2})?\s*(?:am|pm))\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:tomorrow|today|tonight)\b.*$"""), "")
            .trim()
        if (cleanedTask.isBlank()) cleanedTask = utterance

        return ConversationalTaskExtraction(
            task = cleanedTask.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() },
            location = if (locQuery != null) ConversationalExtractedLocation(query = locQuery, specificity = locSpecificity, trigger = locTrigger) else null,
            time = if (timeQuery != null) ConversationalExtractedTime(query = timeQuery, type = timeType) else null,
            confidence = confidence,
            ambiguous_spans = ambiguousSpans,
            clarification_question = question,
            clarification_options = options
        )
    }

    /**
     * Parses free-form text into structured task fields using Gemini structured JSON mode.
     */
    suspend fun parseTaskFromNaturalLanguage(
        prompt: String,
        frequentLocations: List<String> = emptyList()
    ): ParsedTaskData = withContext(Dispatchers.IO) {
        val fallback = extractFallbackTaskData(prompt, frequentLocations)
        val apiKey = BuildConfig.GEMINI_API_KEY

        val frequentPlacesHint = if (frequentLocations.isNotEmpty()) {
            "User's saved frequent locations: [${frequentLocations.joinToString(", ")}]. "
        } else ""

        val systemInstruction = "You are an expert task and spatial-temporal intent parsing assistant. " +
            "Analyze the given natural language task or transcribed speech input and extract structured task details as pure JSON. " +
            frequentPlacesHint +
            "CRITICAL POLICIES:\n" +
            "1. TITLE: Clean, concise task action. Strip reminder prefixes ('remind me to', 'don't forget to') and remove spatial/location and temporal clauses. Capitalize title properly.\n" +
            "2. MULTI-LOCATION DISAMBIGUATION: When an utterance mentions multiple places (e.g. 'pick up dry cleaning near the gym on my way to Trader Joe\'s' or 'drop off books at library before heading to office'):\n" +
            "   - 'locationName': Set to the primary place where the specific task action occurs (e.g. 'Gym' or 'Dry Cleaner'). Strip prepositions.\n" +
            "   - 'candidateLocations': Array containing all mentioned places in order (e.g. ['Gym', 'Trader Joe\'s']).\n" +
            "   - 'waypointContext': The secondary destination or waypoint clause (e.g. 'on my way to Trader Joe\'s').\n" +
            "3. LOCATION SPECIFICITY: Set 'locationSpecificity' to:\n" +
            "   - 'named_venue' for specific branded stores or venues (e.g. 'Trader Joe\'s', 'Target', 'Walmart', 'Costco', 'Starbucks', 'Aunt Mary\'s House').\n" +
            "   - 'category' for generic categories (e.g. 'dry cleaner', 'pharmacy', 'gas station', 'dentist', 'supermarket', 'post office').\n" +
            "   - 'relative' for personal anchors (e.g. 'Home', 'Work', 'Office', 'Gym', 'School', 'Market').\n" +
            "   - 'none' if no location is mentioned.\n" +
            "4. TRIGGER DIRECTION & PHRASES:\n" +
            "   - 'DEPARTURE': If user mentions leaving/departing/exit/after leaving/departing from (e.g. 'when I leave work').\n" +
            "   - 'ARRIVAL': If user mentions arriving/reaching/getting to/on the way to/before I get to/at (e.g. 'when I get to Trader Joe\'s', 'on my way to gym', 'before I arrive at office').\n" +
            "   - 'PROXIMITY': If user mentions near/by/around (e.g. 'near the gym').\n" +
            "5. RAW SPAN: The exact substring in user text representing the primary location.\n" +
            "6. PRIORITY: 'High' if urgent/asap/important/p0/emergency; 'Low' if low priority/whenever/minor; default 'Medium'.\n" +
            "7. CATEGORY: 'Work', 'Personal', 'Shopping', 'Health', 'Errands', 'Study'.\n" +
            "Return valid JSON only with keys: title, description, category, priority, minutesFromNow, locationName, locationSpecificity, triggerDirection, rawSpan, candidateLocations, waypointContext, subtasks."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.15f
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
                parsed.copy(
                    title = if (parsed.title.isNotBlank()) parsed.title else fallback.title,
                    priority = if (fallback.priority == "High") "High" else if (fallback.priority == "Low" && parsed.priority == "Medium") "Low" else parsed.priority,
                    triggerDirection = if (fallback.triggerDirection == "DEPARTURE") "DEPARTURE" else parsed.triggerDirection,
                    category = if (parsed.category.isBlank() || parsed.category == "General") fallback.category else parsed.category,
                    locationName = if (parsed.locationName.isNullOrBlank()) fallback.locationName else parsed.locationName,
                    locationSpecificity = if (parsed.locationSpecificity == "none" && fallback.locationSpecificity != "none") fallback.locationSpecificity else parsed.locationSpecificity,
                    rawSpan = parsed.rawSpan ?: fallback.rawSpan,
                    candidateLocations = if (parsed.candidateLocations.isNotEmpty()) parsed.candidateLocations else fallback.candidateLocations,
                    waypointContext = parsed.waypointContext ?: fallback.waypointContext,
                    minutesFromNow = parsed.minutesFromNow ?: fallback.minutesFromNow
                )
            } else {
                fallback
            }
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to parse task with Gemini proxy, using local rule engine fallback", e)
            fallback
        }
    }

    /**
     * Helper to clean trailing temporal and priority modifiers from extracted location names.
     */
    private fun cleanTrailingModifiers(raw: String): String {
        return raw
            .replace(Regex("""(?i)\b(?:on\s+(?:the|my)\s+way\s+to|before\s+(?:i\s+)?(?:get|arrive|head|heading))\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:tomorrow|today|tonight|yesterday|urgent|asap|now|later|soon)\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?|\d{1,2}(?::\d{2})?\s*(?:am|pm))\b.*$"""), "")
            .replace(Regex("""(?i)\b(?:in\s+\d+\s*(?:min|mins|minute|minutes|hour|hours|hr|hrs))\b.*$"""), "")
            .trim(' ', ',', '.', ';', '-', ':')
    }

    private fun isTimeOrPriorityWord(raw: String): Boolean {
        val lower = raw.lowercase(java.util.Locale.ROOT).trim()
        return lower in listOf("noon", "midnight", "once", "night", "morning", "afternoon", "asap", "now", "later") ||
            Regex("""^\d{1,2}(?::\d{2})?\s*(?:am|pm)?$""").matches(lower) ||
            Regex("""^\d+\s*(?:min|mins|minute|minutes|hour|hours|hr|hrs)$""").matches(lower)
    }

    private fun capitalizeWords(input: String): String {
        return input.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Deterministic local rule engine for parsing natural language prompts and speech inputs offline or as a fallback.
     */
    fun extractFallbackTaskData(
        prompt: String,
        frequentLocations: List<String> = emptyList()
    ): ParsedTaskData {
        val trimmed = prompt.trim()
        val lower = trimmed.lowercase(java.util.Locale.ROOT)

        val priority = when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("high priority") ||
            lower.contains("critical") || lower.contains("p0") || lower.contains("p1") ||
            lower.contains("important") || lower.contains("emergency") || lower.contains("top priority") -> "High"
            lower.contains("low priority") || lower.contains("whenever") || lower.contains("minor") ||
            lower.contains("p3") || lower.contains("low") -> "Low"
            else -> "Medium"
        }

        val triggerDirection = when {
            lower.contains("leave") || lower.contains("leaving") || lower.contains("depart") ||
            lower.contains("departing") || lower.contains("when i leave") || lower.contains("after leaving") ||
            lower.contains("from ") || lower.contains("exit") -> "DEPARTURE"
            else -> "ARRIVAL"
        }

        val category = when {
            lower.contains("meeting") || lower.contains("presentation") || lower.contains("report") ||
            lower.contains("email") || lower.contains("slide") || lower.contains("office") || lower.contains("work") -> "Work"
            lower.contains("buy") || lower.contains("purchase") || lower.contains("groceries") ||
            lower.contains("store") || lower.contains("mall") || lower.contains("shop") || lower.contains("market") -> "Shopping"
            lower.contains("doctor") || lower.contains("gym") || lower.contains("workout") ||
            lower.contains("medicine") || lower.contains("pharmacy") || lower.contains("clinic") || lower.contains("hospital") -> "Health"
            lower.contains("pick up") || lower.contains("drop off") || lower.contains("dry clean") ||
            lower.contains("bank") || lower.contains("gas") || lower.contains("errand") || lower.contains("post office") -> "Errands"
            lower.contains("study") || lower.contains("exam") || lower.contains("homework") || lower.contains("class") -> "Study"
            else -> "Personal"
        }

        val minutesFromNow: Long? = when {
            lower.contains("in 5 min") || lower.contains("in 5 mins") || lower.contains("in 5 minutes") -> 5L
            lower.contains("in 10 min") || lower.contains("in 10 mins") || lower.contains("in 10 minutes") -> 10L
            lower.contains("in 15 min") || lower.contains("in 15 mins") || lower.contains("in 15 minutes") -> 15L
            lower.contains("in 30 min") || lower.contains("in 30 mins") || lower.contains("in 30 minutes") -> 30L
            lower.contains("in 45 min") || lower.contains("in 45 mins") || lower.contains("in 45 minutes") -> 45L
            lower.contains("in 1 hour") || lower.contains("in an hour") || lower.contains("in 1 hr") -> 60L
            lower.contains("in 2 hours") || lower.contains("in 2 hrs") -> 120L
            lower.contains("tomorrow") -> 1440L
            else -> null
        }

        var extractedLocation: String? = null
        var locationPhraseToRemove: String? = null
        var rawSpan: String? = null
        var locationSpecificity = "none"
        val candidateLocations = mutableListOf<String>()
        var waypointContext: String? = null

        // 1. Waypoint & directional transit patterns: "on my way to X", "before I get to X", "before arriving at X"
        val waypointRegex = Regex("""(?i)\b(?:on\s+(?:the|my)\s+way\s+to|before\s+(?:i\s+)?(?:get|arrive)\s+(?:to|at)|before\s+heading\s+(?:to|home))\s+(?:the\s+)?([a-zA-Z0-9'’.\- ]+)""")
        val waypointMatch = waypointRegex.find(trimmed)
        var waypointPlace: String? = null
        val waypointRange = waypointMatch?.range
        if (waypointMatch != null) {
            val rawWp = cleanTrailingModifiers(waypointMatch.groupValues[1].trim())
            if (rawWp.isNotBlank() && !isTimeOrPriorityWord(rawWp)) {
                waypointPlace = capitalizeWords(rawWp)
                waypointContext = waypointMatch.value
            }
        }

        val actionLocations = mutableListOf<String>()

        // 2. User's saved frequent locations (fast-path zero latency)
        for (frequentLoc in frequentLocations) {
            if (frequentLoc.isNotBlank()) {
                val matches = Regex("""(?i)\b(?:at|near|by|from|in|to)?\s*(?:the\s+)?\b${Regex.escape(frequentLoc)}\b""").findAll(trimmed)
                for (match in matches) {
                    val placeCap = capitalizeWords(frequentLoc)
                    if (waypointRange != null && match.range.first >= waypointRange.first && match.range.last <= waypointRange.last) {
                        // Part of waypoint
                        if (waypointPlace == null) waypointPlace = placeCap
                    } else {
                        if (!actionLocations.contains(placeCap)) {
                            actionLocations.add(placeCap)
                            if (locationPhraseToRemove == null) locationPhraseToRemove = match.value
                        }
                    }
                }
            }
        }

        // 3. Phrasal location triggers: "when I get to X", "when I arrive at X", "when I reach X", "when I leave X"
        val whenRegex = Regex("""(?i)\bwhen\s+(?:i\s+)?(?:get\s+to|arrive\s+at|reach|leave|depart\s+from|am\s+at)\s+(?:the\s+)?([a-zA-Z0-9'’.\- ]+)""")
        val whenMatches = whenRegex.findAll(trimmed)
        for (whenMatch in whenMatches) {
            val rawLoc = whenMatch.groupValues[1].trim()
            val cleanedLoc = cleanTrailingModifiers(rawLoc)
            if (cleanedLoc.isNotBlank() && !isTimeOrPriorityWord(cleanedLoc)) {
                val cap = capitalizeWords(cleanedLoc)
                if (waypointRange != null && whenMatch.range.first >= waypointRange.first && whenMatch.range.last <= waypointRange.last) {
                    if (waypointPlace == null) waypointPlace = cap
                } else {
                    if (!actionLocations.contains(cap)) {
                        actionLocations.add(cap)
                        if (locationPhraseToRemove == null) locationPhraseToRemove = whenMatch.value
                    }
                }
            }
        }

        // 4. Spatial prepositions & directional motion: "near X", "at X", "heading to X"
        val prepRegex = Regex("""(?i)\b(?:near|at|from|by|inside|around|heading\s+to|heading\s+towards|go\s+to|drive\s+to|walk\s+to)\s+(?:the\s+)?([a-zA-Z0-9'’.\- ]+)""")
        val prepMatches = prepRegex.findAll(trimmed)
        for (match in prepMatches) {
            val rawLoc = match.groupValues[1].trim()
            val cleanedLoc = cleanTrailingModifiers(rawLoc)
            if (cleanedLoc.isNotBlank() && !isTimeOrPriorityWord(cleanedLoc)) {
                val cap = capitalizeWords(cleanedLoc)
                if (waypointRange != null && match.range.first >= waypointRange.first && match.range.last <= waypointRange.last) {
                    if (waypointPlace == null) waypointPlace = cap
                } else {
                    if (!actionLocations.contains(cap)) {
                        actionLocations.add(cap)
                        if (locationPhraseToRemove == null) locationPhraseToRemove = match.value
                    }
                }
            }
        }

        // 5. Known entity match (store chains, common places, relative anchors)
        val knownEntities = listOf(
            "walmart" to ("Walmart" to "named_venue"),
            "target" to ("Target" to "named_venue"),
            "costco" to ("Costco" to "named_venue"),
            "trader joe's" to ("Trader Joe's" to "named_venue"),
            "trader joes" to ("Trader Joe's" to "named_venue"),
            "whole foods" to ("Whole Foods" to "named_venue"),
            "starbucks" to ("Starbucks" to "named_venue"),
            "post office" to ("Post Office" to "category"),
            "walgreens" to ("Walgreens" to "named_venue"),
            "cvs" to ("CVS" to "named_venue"),
            "home depot" to ("Home Depot" to "named_venue"),
            "lowe's" to ("Lowe's" to "named_venue"),
            "lowes" to ("Lowe's" to "named_venue"),
            "supermarket" to ("Supermarket" to "category"),
            "grocery store" to ("Grocery Store" to "category"),
            "grocery" to ("Grocery Store" to "category"),
            "pharmacy" to ("Pharmacy" to "category"),
            "airport" to ("Airport" to "category"),
            "library" to ("Library" to "category"),
            "hospital" to ("Hospital" to "category"),
            "school" to ("School" to "relative"),
            "office" to ("Office" to "relative"),
            "work" to ("Work" to "relative"),
            "gym" to ("Gym" to "relative"),
            "home" to ("Home" to "relative"),
            "dry cleaner" to ("Dry Cleaner" to "category"),
            "dry cleaners" to ("Dry Cleaner" to "category"),
            "dentist" to ("Dentist" to "category"),
            "doctor" to ("Doctor" to "category"),
            "gas station" to ("Gas Station" to "category")
        )
        for ((key, pair) in knownEntities) {
            val (label, _) = pair
            val matches = Regex("""(?i)\b(?:at|near|from|by|in|to)?\s*(?:the\s+)?\b$key\b""").findAll(trimmed)
            for (match in matches) {
                if (waypointRange != null && match.range.first >= waypointRange.first && match.range.last <= waypointRange.last) {
                    if (waypointPlace == null) waypointPlace = label
                } else {
                    if (!actionLocations.contains(label)) {
                        actionLocations.add(label)
                        if (locationPhraseToRemove == null) locationPhraseToRemove = match.value
                    }
                }
            }
        }

        // Policy: Action location is primary target; waypoint is candidate/secondary
        if (actionLocations.isNotEmpty()) {
            extractedLocation = actionLocations.first()
            locationSpecificity = if (extractedLocation.lowercase() in listOf("home", "work", "office", "gym", "school")) "relative" else "named_venue"
            candidateLocations.addAll(actionLocations)
        } else if (waypointPlace != null) {
            extractedLocation = waypointPlace
            locationSpecificity = if (waypointPlace.lowercase() in listOf("home", "work", "office", "gym", "school")) "relative" else "named_venue"
        }

        // Add waypoint to candidate locations if not already present
        if (waypointPlace != null && !candidateLocations.contains(waypointPlace)) {
            candidateLocations.add(waypointPlace)
        }

        // Clean task title: strip "remind me to", "remember to", location phrase, waypoint clause, etc.
        var cleanTitle = trimmed
        if (locationPhraseToRemove != null) {
            cleanTitle = cleanTitle.replace(locationPhraseToRemove, "", ignoreCase = true).trim()
        }
        if (waypointMatch != null) {
            cleanTitle = cleanTitle.replace(waypointMatch.value, "", ignoreCase = true).trim()
        }
        cleanTitle = cleanTitle
            .replace(Regex("""(?i)^(?:remind me to|remind me|remember to|don't forget to|please|i need to)\s+"""), "")
            .replace(Regex("""(?i)\b(?:urgent|asap|high priority|low priority)\b"""), "")
            .replace(Regex("""(?i)\b(?:tomorrow|today|tonight|in \d+\s*(?:min|mins|minute|minutes|hour|hours|hr|hrs))\b"""), "")
            .trim(' ', ',', '.', ';', '-')

        if (cleanTitle.isBlank()) {
            cleanTitle = if (extractedLocation != null) "Visit $extractedLocation" else prompt.trim()
        }
        cleanTitle = cleanTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        if (cleanTitle.length > 60) {
            cleanTitle = cleanTitle.take(60).trimEnd() + "..."
        }

        return ParsedTaskData(
            title = cleanTitle.ifBlank { "New Task" },
            category = category,
            priority = priority,
            minutesFromNow = minutesFromNow,
            locationName = extractedLocation,
            locationSpecificity = locationSpecificity,
            triggerDirection = triggerDirection,
            rawSpan = rawSpan,
            candidateLocations = candidateLocations,
            waypointContext = waypointContext
        )
    }

    suspend fun extractStructuredSchedulingData(taskDescription: String): StructuredSchedulingData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fallback = extractFallbackSchedulingData(taskDescription, now)
        val apiKey = BuildConfig.GEMINI_API_KEY

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
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to extract structured scheduling data with Gemini proxy, using fallback", e)
            fallback
        }
    }

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

    suspend fun generateSubtasks(taskTitle: String): List<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
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
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to generate subtasks", e)
            emptyList()
        }
    }

    suspend fun generateDailyBriefing(tasks: List<Task>, userName: String? = null): String = withContext(Dispatchers.IO) {
        val pendingTasks = tasks.filter { !it.isDone }
        val nameGreeting = if (!userName.isNullOrBlank()) ", $userName" else ""
        if (pendingTasks.isEmpty()) {
            return@withContext "You're all clear$nameGreeting! No pending tasks right now. Great job staying on top of your day."
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val taskTitles = pendingTasks.take(6).joinToString(", ") { "${it.title} (${it.priority} priority)" }
        val personalInstruction = if (!userName.isNullOrBlank()) {
            " The user's name is '$userName'. Address $userName warmly and personally by name."
        } else ""
        val prompt = "Based on these pending tasks: $taskTitles. Write a short, motivating, 1 to 2 sentence focus recommendation for today.$personalInstruction Keep it friendly, encouraging, and natural."

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
                ?: if (!userName.isNullOrBlank()) "Stay focused, $userName, and tackle your highest priority tasks first today!"
                   else "Stay focused and tackle your highest priority tasks first today!"
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to generate daily briefing", e)
            if (!userName.isNullOrBlank()) "Stay focused, $userName, and tackle your highest priority tasks first today!"
            else "Stay focused and tackle your highest priority tasks first today!"
        }
    }

    suspend fun suggestOptimalSchedule(
        taskTitle: String,
        taskPriority: String = "Medium",
        existingTasks: List<Task> = emptyList(),
        existingCommitments: String? = null
    ): ScheduleSuggestion = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val pending = existingTasks.filter { !it.isDone }.take(8)
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
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to get optimal schedule from Gemini proxy", e)
            ScheduleSuggestion(
                timeSlotText = "Today at 3:00 PM",
                suggestedMinutesFromNow = 180,
                reasoning = "Recommended focus time slot based on current task list."
            )
        }
    }

    /**
     * Analyzes a user's task list and uses Gemini AI API to suggest an optimized daily schedule based on priority.
     */
    suspend fun analyzeTaskListAndSuggestOptimizedDailySchedule(
        tasks: List<Task>
    ): OptimizedDailySchedule = withContext(Dispatchers.IO) {
        val activeTasks = tasks.filter { !it.isDone && !it.isSoftDeleted }
        if (activeTasks.isEmpty()) {
            return@withContext OptimizedDailySchedule(
                overallSummary = "Your task list is empty! Add tasks to generate an AI-optimized schedule.",
                recommendedFocusBlocks = emptyList(),
                productivityTip = "Enjoy your free time or add new goals to plan your day."
            )
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val tasksDescription = activeTasks.joinToString("\n") { task ->
            val dueStr = if (task.dueDate != null) {
                val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(task.dueDate - System.currentTimeMillis())
                if (hours in 0..24) "due in ${hours}h" else "due date set"
            } else "no fixed due time"
            "- ID ${task.id}: \"${task.safeTitle}\" [Priority: ${task.safePriority}, Status: ${task.safeStatus}, $dueStr, Category: ${task.safeCategory}]"
        }

        val systemInstruction = "You are an AI Daily Productivity & Workload Scheduler. " +
            "Analyze the user's task list (prioritizing High > Medium > Low priority tasks and upcoming due dates) " +
            "and suggest an optimized, realistic daily schedule with specific time slots. " +
            "Return valid JSON only with structure:\n" +
            "{\n" +
            "  \"overallSummary\": \"Concise overview of the day's plan\",\n" +
            "  \"recommendedFocusBlocks\": [\n" +
            "    {\n" +
            "      \"timeSlot\": \"09:00 AM - 10:30 AM\",\n" +
            "      \"taskId\": 1,\n" +
            "      \"taskTitle\": \"Task Name\",\n" +
            "      \"priority\": \"High\",\n" +
            "      \"category\": \"Category\",\n" +
            "      \"reasoning\": \"Why this task is placed in this slot\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"productivityTip\": \"Actionable productivity advice\"\n" +
            "}"

        val prompt = "Analyze the following task list and build an optimized daily schedule:\n\n$tasksDescription"

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
                RetrofitClient.jsonInstance.decodeFromString<OptimizedDailySchedule>(cleanJson)
            } else {
                buildFallbackSchedule(activeTasks)
            }
        } catch (e: Exception) {
            safeLogE(TAG, "Failed to analyze task list with Gemini, building priority fallback schedule", e)
            buildFallbackSchedule(activeTasks)
        }
    }

    private fun buildFallbackSchedule(activeTasks: List<Task>): OptimizedDailySchedule {
        val sorted = activeTasks.sortedWith(
            compareByDescending<Task> { it.safePriority == "High" }
                .thenByDescending { it.safePriority == "Medium" }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
        )

        val slots = listOf(
            "09:00 AM - 10:30 AM",
            "10:45 AM - 12:00 PM",
            "01:30 PM - 03:00 PM",
            "03:15 PM - 04:30 PM",
            "05:00 PM - 06:00 PM"
        )

        val focusBlocks = sorted.take(slots.size).mapIndexed { index, task ->
            ScheduledTimeSlot(
                timeSlot = slots[index],
                taskId = task.id,
                taskTitle = task.safeTitle,
                priority = task.safePriority,
                category = task.safeCategory,
                reasoning = when (task.safePriority) {
                    "High" -> "High-priority task scheduled during morning peak energy hours."
                    "Medium" -> "Medium-priority task placed in early afternoon focus window."
                    else -> "Low-priority task assigned to late afternoon block."
                }
            )
        }

        return OptimizedDailySchedule(
            overallSummary = "Daily schedule optimized by priority (${sorted.count { it.safePriority == "High" }} high-priority tasks scheduled first).",
            recommendedFocusBlocks = focusBlocks,
            productivityTip = "Focus on one high-priority task at a time without multitasking."
        )
    }

    private suspend fun executeGenerateContent(apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
        val fullPromptBuilder = java.lang.StringBuilder()
        request.systemInstruction?.parts?.forEach { part ->
            part.text?.let { fullPromptBuilder.append(it).append("\n\n") }
        }
        request.contents.forEach { content ->
            content.parts.forEach { part ->
                part.text?.let { fullPromptBuilder.append(it).append("\n") }
            }
        }
        val promptStr = fullPromptBuilder.toString().trim()

        try {
            val proxyResp = RetrofitClient.proxyService.generateContentProxy(ProxyGeminiRequest(prompt = promptStr))
            val proxyText = proxyResp.text
            if (!proxyText.isNullOrBlank()) {
                return GenerateContentResponse(
                    candidates = listOf(
                        Candidate(content = Content(parts = listOf(Part(text = proxyText))))
                    )
                )
            }
        } catch (e: Exception) {
            safeLogW(TAG, "Cloud Functions proxy call failed/unreachable, checking client API key fallback...", e)
        }

        if (apiKey.isNotBlank()) {
            return try {
                RetrofitClient.service.generateContent(apiKey, request)
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    safeLogW(TAG, "gemini-3.5-flash endpoint returned 404, falling back to gemini-flash-latest")
                    RetrofitClient.service.generateContentWithModel("gemini-flash-latest", apiKey, request)
                } else {
                    throw e
                }
            }
        }

        throw IllegalStateException("No backend proxy response or local API key available")
    }
}
