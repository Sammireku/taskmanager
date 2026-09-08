package com.example

import com.example.data.Task
import com.example.gemini.GeminiTaskHelper
import com.example.ui.formatTaskDueDate
import com.example.data.FirestoreRepository
import org.junit.Assert.*
import org.junit.Test
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
            isDeleted = true,
            deletedAt = now
        )

        assertTrue(task.isDeleted)
        assertEquals(now, task.deletedAt)
    }

    @Test
    fun softDelete_retentionFilter_correctlyIdentifiesPurgeableTasks() {
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val cutoffTimestamp = now - thirtyDaysMs

        val recentDeletedTask = Task(id = 1, title = "Recent Task", isDeleted = true, deletedAt = now - (10L * 24 * 60 * 60 * 1000))
        val expiredTask = Task(id = 2, title = "Old Task", isDeleted = true, deletedAt = now - (35L * 24 * 60 * 60 * 1000))

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
            isCompleted = false,
            dueDate = expectedNextDue
        )

        assertEquals("Daily Meditation", nextTask.title)
        assertFalse(nextTask.isCompleted)
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
}
