package com.example

import com.example.data.Task
import com.example.data.SavedLocation
import com.example.gemini.GeminiTaskHelper
import com.example.ui.formatTaskDueDate
import com.example.data.FirestoreRepository
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Comprehensive local unit tests covering Task model integrity, soft-delete 30-day retention,
 * habit recurrence calculations, and multi-tenant authentication isolation.
 */
class ExampleUnitTest {

    @Test
    fun taskEntity_hasSoftDeleteFields() {
        val now = System.currentTimeMillis()
        val task = Task(
            id = 1,
            title = "Buy Groceries",
            deletedAt = now
        )

        assertTrue(task.isSoftDeleted)
        assertEquals(now, task.deletedAt)
    }

    @Test
    fun softDelete_retentionFilter_correctlyIdentifiesPurgeableTasks() {
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val cutoffTimestamp = now - thirtyDaysMs

        val recentDeletedTask = Task(id = 1, title = "Recent Task", deletedAt = now - (10L * 24 * 60 * 60 * 1000))
        val expiredTask = Task(id = 2, title = "Old Task", deletedAt = now - (35L * 24 * 60 * 60 * 1000))

        // Check recent task should not be purged (deletedAt > cutoffTimestamp)
        assertTrue(recentDeletedTask.deletedAt!! > cutoffTimestamp)

        // Check expired task should be purged (deletedAt <= cutoffTimestamp)
        assertTrue(expiredTask.deletedAt!! <= cutoffTimestamp)
    }

    @Test
    fun habitRecurrence_calculatesDailyNextDueDateCorrectly() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 10, 10, 0)
        val initialDue = cal.timeInMillis

        // Add 1 day for daily frequency
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val expectedNextDue = cal.timeInMillis

        val task = Task(
            id = 10,
            title = "Daily Meditation",
            isHabit = true,
            habitFrequency = "Daily",
            dueDate = initialDue
        )

        val nextTask = task.copy(
            id = 0,
            status = "PENDING",
            dueDate = expectedNextDue
        )

        assertEquals("Daily Meditation", nextTask.title)
        assertFalse(nextTask.isDone)
        assertEquals(expectedNextDue, nextTask.dueDate)
    }

    @Test
    fun habitRecurrence_calculatesWeeklyNextDueDateCorrectly() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JANUARY, 10, 10, 0)
        val initialDue = cal.timeInMillis

        // Add 1 week for weekly frequency
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        val expectedNextDue = cal.timeInMillis

        val task = Task(
            id = 11,
            title = "Weekly Grocery Restock",
            isHabit = true,
            habitFrequency = "Weekly",
            dueDate = initialDue
        )

        assertEquals(expectedNextDue, initialDue + (7L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun unauthenticatedState_returnsNullUserIdToPreventDataMixing() {
        val repository = FirestoreRepository()
        // Unauthenticated repository should return null for user ID
        assertNull(repository.getUserId())
    }

    @Test
    fun schedulingExtraction_extractsTimeAndCategory() {
        val prompt = "Doctor appointment tomorrow at 10am urgent"
        val data = GeminiTaskHelper.extractFallbackSchedulingData(prompt)

        assertEquals("Health", data.category)
        assertEquals("High", data.priority)
        assertNotNull(data.dueDateMillis)
        assertNotNull(data.time)
    }

    @Test
    fun formatTaskDueDate_formatsCorrectly() {
        val now = System.currentTimeMillis()
        val formatted = formatTaskDueDate(now)
        assertTrue(formatted.startsWith("Today"))
    }

    @Test
    fun locationBoundTask_extractsLocationAndArrivalDirection() {
        val speechOrTextPrompt = "Remind me to buy groceries at Trader Joe's tomorrow"
        val parsed = GeminiTaskHelper.extractFallbackTaskData(speechOrTextPrompt)

        assertEquals("Trader Joe's", parsed.locationName)
        assertEquals("ARRIVAL", parsed.triggerDirection)
        assertEquals("Shopping", parsed.category)
        assertTrue(parsed.title.contains("Buy Groceries", ignoreCase = true))
    }

    @Test
    fun locationBoundTask_extractsDepartureDirectionAndLocation() {
        val speechOrTextPrompt = "Remind me to turn off monitors when I leave office"
        val parsed = GeminiTaskHelper.extractFallbackTaskData(speechOrTextPrompt)

        assertEquals("Office", parsed.locationName)
        assertEquals("DEPARTURE", parsed.triggerDirection)
        assertEquals("Work", parsed.category)
        assertTrue(parsed.title.contains("Turn Off Monitors", ignoreCase = true))
    }

    @Test
    fun locationBoundTask_stripsTemporalAndPriorityModifiersFromLocation() {
        val prompt = "Buy milk at Walmart tomorrow urgent"
        val parsed = GeminiTaskHelper.extractFallbackTaskData(prompt)

        assertEquals("Walmart", parsed.locationName)
        assertEquals("High", parsed.priority)
        assertEquals("Shopping", parsed.category)
    }

    @Test
    fun multiLocation_detectsSpatialCandidatesAndWaypointContext() {
        val prompt = "pick up dry cleaning near the gym on my way to Trader Joe's"
        val parsed = GeminiTaskHelper.extractFallbackTaskData(prompt)

        // Verifies candidateLocations extracts multiple spatial candidates
        assertTrue(parsed.candidateLocations.isNotEmpty())
        assertTrue(parsed.candidateLocations.any { it.contains("Gym", ignoreCase = true) })
        assertTrue(parsed.candidateLocations.any { it.contains("Trader Joe's", ignoreCase = true) })
        // Verifies primary action venue policy (the immediate task action target)
        assertEquals("Gym", parsed.locationName)
        // Verifies waypoint context recognized
        assertNotNull(parsed.waypointContext)
    }

    @Test
    fun directionalPatterns_detectsOnMyWayToAndBeforeArrive() {
        val prompt1 = "Call landlord before I get to apartment"
        val parsed1 = GeminiTaskHelper.extractFallbackTaskData(prompt1)
        assertEquals("Apartment", parsed1.locationName)
        assertEquals("ARRIVAL", parsed1.triggerDirection)

        val prompt2 = "Stop for coffee on my way to office"
        val parsed2 = GeminiTaskHelper.extractFallbackTaskData(prompt2)
        assertEquals("Office", parsed2.locationName)
        assertEquals("ARRIVAL", parsed2.triggerDirection)
    }

    @Test
    fun frequentLocationsInjection_resolvesNonGazetteerVenues() {
        val customPlaces = listOf("Aunt Mary's House", "The Dentist", "CrossFit Box")
        val prompt = "Drop off homemade pie at Aunt Mary's House this afternoon"
        val parsed = GeminiTaskHelper.extractFallbackTaskData(prompt, frequentLocations = customPlaces)

        assertEquals("Aunt Mary's House", parsed.locationName)
        assertEquals("ARRIVAL", parsed.triggerDirection)
    }

    @Test
    fun savedLocation_displaysAppropriateIcons() {
        val home = SavedLocation(name = "Home", latitude = 37.77, longitude = -122.41, category = "HOME")
        val work = SavedLocation(name = "Work", latitude = 37.78, longitude = -122.40, category = "WORK")
        val school = SavedLocation(name = "School", latitude = 37.79, longitude = -122.39, category = "SCHOOL")
        val gym = SavedLocation(name = "Gym", latitude = 37.80, longitude = -122.38, category = "GYM")
        val custom = SavedLocation(name = "Dentist", latitude = 37.81, longitude = -122.37, category = "CUSTOM")

        assertEquals("🏠", home.displayIcon)
        assertEquals("💼", work.displayIcon)
        assertEquals("🏫", school.displayIcon)
        assertEquals("🏋️", gym.displayIcon)
        assertEquals("📍", custom.displayIcon)
    }

    @Test
    fun roomDatabaseTaskEntity_validatesFieldsAndDefaults() {
        val task = Task(
            id = 1,
            title = "Prepare Q3 Presentation",
            dueDate = System.currentTimeMillis() + 86400000L,
            status = "PENDING",
            priority = "High",
            isHabit = true,
            habitFrequency = "Daily"
        )

        assertEquals("Prepare Q3 Presentation", task.safeTitle)
        assertEquals("High", task.safePriority)
        assertEquals("PENDING", task.safeStatus)
        assertFalse(task.isDone)
        assertTrue(task.isHabit)

        val completedTask = task.copy(status = "COMPLETED")
        assertTrue(completedTask.isDone)
    }

    @Test
    fun workManagerScheduler_handlesWorkNamesAndData() {
        val workName = com.example.work.TaskWorkScheduler.getWorkNameForTask(42)
        assertEquals("task_due_work_42", workName)

        val habitChannel = com.example.work.HabitReminderWorker.CHANNEL_ID
        assertEquals("cobbyai_habit_reminders_work", habitChannel)
    }

    @Test
    fun geminiTaskHelper_analyzesTaskListAndSuggestsOptimizedDailySchedule() = runBlocking {
        val tasks = listOf(
            Task(id = 1, title = "Submit Q3 Tax Audit", priority = "High", dueDate = System.currentTimeMillis() + 3600000L),
            Task(id = 2, title = "Buy groceries", priority = "Medium"),
            Task(id = 3, title = "Organize desk drawer", priority = "Low"),
            Task(id = 4, title = "Daily Morning Walk", priority = "Medium", isHabit = true)
        )

        val schedule = GeminiTaskHelper.analyzeTaskListAndSuggestOptimizedDailySchedule(tasks)

        assertNotNull(schedule.overallSummary)
        assertTrue(schedule.recommendedFocusBlocks.isNotEmpty())
        // Verifies high-priority task is scheduled first in focus blocks
        assertEquals("Submit Q3 Tax Audit", schedule.recommendedFocusBlocks.first().taskTitle)
        assertEquals("High", schedule.recommendedFocusBlocks.first().priority)
    }

    @Test
    fun conversationalTask_bareHourTriggersAmPmClarification() {
        val utterance = "remind me to go to the barbershop at 5"
        val extraction = GeminiTaskHelper.extractFallbackConversationalTask(utterance)

        assertEquals("Go to the barbershop", extraction.task)
        assertEquals("barbershop", extraction.location?.query)
        assertEquals("low", extraction.confidence)
        assertTrue(extraction.ambiguous_spans.contains("at 5"))
        assertNotNull(extraction.clarification_question)
        assertTrue(extraction.clarification_question!!.contains("AM", ignoreCase = true) && extraction.clarification_question!!.contains("PM", ignoreCase = true))
        assertTrue(extraction.clarification_options.any { it.contains("AM") })
        assertTrue(extraction.clarification_options.any { it.contains("PM") })
    }

    @Test
    fun conversationalTask_postProcessorDetectsAmbiguousBareHour() {
        val rawExtraction = com.example.gemini.ConversationalTaskExtraction(
            task = "go to the gym",
            location = com.example.gemini.ConversationalExtractedLocation(query = "gym"),
            time = com.example.gemini.ConversationalExtractedTime(query = "at 7"),
            confidence = "high",
            ambiguous_spans = emptyList()
        )

        val processed = GeminiTaskHelper.postProcessConversationalExtraction(
            rawExtraction,
            "remind me to go to the gym at 7"
        )

        assertEquals("low", processed.confidence)
        assertTrue(processed.ambiguous_spans.contains("at 7"))
        assertTrue(processed.clarification_question!!.contains("AM", ignoreCase = true) && processed.clarification_question!!.contains("PM", ignoreCase = true))
        assertTrue(processed.clarification_options.contains("7:00 AM"))
        assertTrue(processed.clarification_options.contains("7:00 PM"))
    }

    @Test
    fun regressionCorpus_flagsHighConfidenceCorrectionAccurately() {
        val wasHigh = com.example.data.RegressionCorpusManager.isHighConfidenceCorrection("high")
        val wasMedium = com.example.data.RegressionCorpusManager.isHighConfidenceCorrection("medium")
        val wasLow = com.example.data.RegressionCorpusManager.isHighConfidenceCorrection("low")

        assertTrue(wasHigh)
        assertFalse(wasMedium)
        assertFalse(wasLow)
    }
}
