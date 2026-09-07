package com.example.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.BuildConfig

// --- Data Classes ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
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
data class ParsedTaskData(
    val title: String,
    val description: String? = null,
    val category: String = "General",
    val priority: String = "Medium",
    val minutesFromNow: Long? = null,
    val locationName: String? = null,
    val subtasks: List<String> = emptyList(),
    val triggerDirection: String = "ARRIVAL"
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

// --- Retrofit Setup ---

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
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

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
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(jsonInstance.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}
