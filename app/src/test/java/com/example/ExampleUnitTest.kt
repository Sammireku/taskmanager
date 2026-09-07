package com.example

import com.example.data.Task
import com.example.gemini.GeminiTaskHelper
import com.example.ui.formatTaskDueDate
import org.junit.Assert.*
import org.junit.Test

/**
 * Local unit tests verifying Task entity, Gemini scheduling extraction, and UI helpers.
 */
class ExampleUnitTest {
    @Test
    fun taskEntity_hasAllRequiredFields() {
        val dueTime = System.currentTimeMillis() + 3600000L
        val task = Task(
            id = 1,
            title = "Doctor appointment",
            description = "Annual checkup and blood tests",
            dueDate = dueTime,
            priority = "High",
            isCompleted = false
        )

        assertEquals("Doctor appointment", task.title)
        assertEquals("Annual checkup and blood tests", task.description)
        assertEquals(dueTime, task.dueDate)
        assertEquals("High", task.priority)
        assertFalse(task.isCompleted)
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

