package com.example.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- Data Classes ---

@Serializable
data class ProxyGeminiRequest(
    val prompt: String
)

@Serializable
data class ProxyGeminiResponse(
    val text: String? = null,
    val error: String? = null
)

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: kotlinx.serialization.json.JsonObject? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

@Serializable
data class ConversationalExtractedLocation(
    val query: String? = null,
    val specificity: String = "none", // named_venue, category, relative_to_user, referential, none
    val trigger: String = "none" // arrival, departure, proximity, none
)

@Serializable
data class ConversationalExtractedTime(
    val query: String? = null,
    val type: String = "none" // absolute, relative, recurring, none
)

@Serializable
data class ConversationalTaskExtraction(
    val task: String = "",
    val location: ConversationalExtractedLocation? = null,
    val time: ConversationalExtractedTime? = null,
    val confidence: String = "medium", // high, medium, low
    val ambiguous_spans: List<String> = emptyList(),
    val clarification_question: String? = null,
    val clarification_options: List<String> = emptyList()
)

@Serializable
data class ParsedTaskData(
    val title: String,
    val description: String? = null,
    val category: String = "General",
    val priority: String = "Medium",
    val minutesFromNow: Long? = null,
    val locationName: String? = null,
    val locationSpecificity: String = "none", // named_venue, category, relative, none
    val triggerDirection: String = "ARRIVAL", // ARRIVAL, DEPARTURE, PROXIMITY
    val rawSpan: String? = null,
    val candidateLocations: List<String> = emptyList(),
    val waypointContext: String? = null,
    val subtasks: List<String> = emptyList()
)

@Serializable
data class ScheduleSuggestion(
    val timeSlotText: String = "Today at 3:00 PM",
    val suggestedMinutesFromNow: Long? = 180,
    val reasoning: String = "Optimal focus time available between existing commitments."
)

@Serializable
data class StructuredSchedulingData(
    val title: String,
    val time: String? = null,
    val category: String = "General",
    val dueDateMillis: Long? = null,
    val priority: String = "Medium",
    val description: String? = null
)

@Serializable
data class ScheduledTimeSlot(
    val timeSlot: String,
    val taskId: Int? = null,
    val taskTitle: String,
    val priority: String = "Medium",
    val category: String? = "Task",
    val reasoning: String = "Scheduled based on priority and urgency."
)

@Serializable
data class OptimizedDailySchedule(
    val overallSummary: String = "Optimized daily schedule based on priority.",
    val recommendedFocusBlocks: List<ScheduledTimeSlot> = emptyList(),
    val productivityTip: String = "Take short breaks between intense focus blocks."
)

// --- Retrofit Setup ---

interface GeminiProxyApiService {
    @POST("gemini/generate")
    suspend fun generateContentProxy(
        @Body request: ProxyGeminiRequest
    ): ProxyGeminiResponse
}

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentWithModel(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val DIRECT_BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val PROXY_BASE_URL = "https://us-central1-cobby-tasks.cloudfunctions.net/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val jsonInstance = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(DIRECT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(jsonInstance.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    val proxyService: GeminiProxyApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(PROXY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(jsonInstance.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiProxyApiService::class.java)
    }
}
