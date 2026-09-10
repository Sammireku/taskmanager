package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class RegressionCorpusEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val utterance: String,
    val initialTask: String,
    val initialLocation: String? = null,
    val initialTime: String? = null,
    val initialConfidence: String,
    val ambiguousSpans: List<String> = emptyList(),
    val userCorrection: String,
    val resolvedValue: String,
    val wasHighConfidenceCorrected: Boolean = false
)

/**
 * Manages the regression corpus log of cases where users corrected extractions,
 * specifically highlighting high-confidence extractions that needed user correction.
 */
class RegressionCorpusManager(context: Context) {
    private val prefs = context.getSharedPreferences("cobby_regression_corpus", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    private val _entries = MutableStateFlow<List<RegressionCorpusEntry>>(loadEntries())
    val entries = _entries.asStateFlow()

    companion object {
        private const val KEY_ENTRIES = "key_corpus_entries"
        private const val TAG = "RegressionCorpus"

        fun isHighConfidenceCorrection(confidence: String): Boolean {
            return confidence.equals("high", ignoreCase = true)
        }
    }

    private fun loadEntries(): List<RegressionCorpusEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun logCorrection(
        utterance: String,
        initialTask: String,
        initialLocation: String?,
        initialTime: String?,
        initialConfidence: String,
        ambiguousSpans: List<String>,
        userCorrection: String,
        resolvedValue: String
    ) {
        val isHighConfidence = initialConfidence.equals("high", ignoreCase = true)
        val entry = RegressionCorpusEntry(
            utterance = utterance,
            initialTask = initialTask,
            initialLocation = initialLocation,
            initialTime = initialTime,
            initialConfidence = initialConfidence,
            ambiguousSpans = ambiguousSpans,
            userCorrection = userCorrection,
            resolvedValue = resolvedValue,
            wasHighConfidenceCorrected = isHighConfidence
        )

        if (isHighConfidence) {
            Log.w(
                TAG,
                "🚨 HIGH-CONFIDENCE EXTRACTION CORRECTED BY USER! Utterance: \"$utterance\" | Initial: task=\"$initialTask\", time=\"$initialTime\", loc=\"$initialLocation\" | Correction: \"$userCorrection\" -> \"$resolvedValue\""
            )
        } else {
            Log.i(
                TAG,
                "Disambiguation resolved by user: \"$utterance\" -> \"$resolvedValue\""
            )
        }

        val current = _entries.value.toMutableList()
        current.add(0, entry)
        val trimmed = current.take(100)
        _entries.value = trimmed
        try {
            prefs.edit().putString(KEY_ENTRIES, json.encodeToString(trimmed)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save regression corpus entry", e)
        }
    }

    fun clearCorpus() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY_ENTRIES).apply()
    }
}
