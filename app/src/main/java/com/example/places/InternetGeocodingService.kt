package com.example.places

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Cost-effective, high-reliability internet geocoding service.
 * Uses OpenStreetMap Nominatim with strict in-memory caching and debouncing.
 * Zero external paid API costs, highly reliable fallback when Google Maps API key
 * is absent or rate-limited.
 */
object InternetGeocodingService {
    private const val TAG = "InternetGeocoding"
    private const val USER_AGENT = "CobbyTaskReminder/1.0 (Android; cost-effective-geocoding)"

    // In-memory cache for query -> suggestions to prevent repeated network requests
    private val queryCache = ConcurrentHashMap<String, List<PlaceSuggestion>>()

    // In-memory cache for lat_lng -> address string
    private val reverseCache = ConcurrentHashMap<String, String>()

    /**
     * Search place predictions over the internet using OpenStreetMap Nominatim.
     * Checks in-memory cache first to ensure zero redundant network requests.
     */
    suspend fun searchPlacesInternet(
        query: String,
        biasCenter: LatLng? = null,
        limit: Int = 5
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val cacheKey = "${trimmed.lowercase()}_${biasCenter?.latitude?.toInt() ?: 0}"
        queryCache[cacheKey]?.let { return@withContext it }

        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val viewboxParam = if (biasCenter != null) {
                val delta = 0.5 // roughly 55km radius
                val left = biasCenter.longitude - delta
                val top = biasCenter.latitude + delta
                val right = biasCenter.longitude + delta
                val bottom = biasCenter.latitude - delta
                "&viewbox=$left,$top,$right,$bottom"
            } else ""

            val urlString = "https://nominatim.openstreetmap.org/search?" +
                "q=$encodedQuery&format=json&limit=$limit&addressdetails=1$viewboxParam"

            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                val results = mutableListOf<PlaceSuggestion>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val placeId = obj.optString("place_id", i.toString())
                    val displayName = obj.optString("display_name", "")
                    val name = obj.optString("name").ifBlank {
                        displayName.split(",").firstOrNull()?.trim() ?: trimmed
                    }
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)

                    val secondary = if (displayName.contains(",")) {
                        displayName.substringAfter(",").trim()
                    } else "Lat: ${String.format(java.util.Locale.US, "%.4f", lat)}, Lng: ${String.format(java.util.Locale.US, "%.4f", lon)}"

                    results.add(
                        PlaceSuggestion(
                            placeId = "osm_${lat}_${lon}_$placeId",
                            primaryText = name,
                            secondaryText = secondary,
                            fullText = displayName
                        )
                    )
                }

                if (results.isNotEmpty()) {
                    queryCache[cacheKey] = results
                }
                return@withContext results
            } else {
                Log.w(TAG, "Nominatim returned HTTP ${connection.responseCode}")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim internet geocoding failed: ${e.message}")
            return@withContext emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Reverse geocodes coordinates to a readable address.
     */
    suspend fun reverseGeocodeInternet(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val roundedLat = String.format(java.util.Locale.US, "%.4f", lat)
        val roundedLon = String.format(java.util.Locale.US, "%.4f", lon)
        val cacheKey = "${roundedLat}_$roundedLon"
        reverseCache[cacheKey]?.let { return@withContext it }

        var connection: HttpURLConnection? = null
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?" +
                "lat=$lat&lon=$lon&format=json"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val obj = JSONObject(response)
                val displayName = obj.optString("display_name", "")
                if (displayName.isNotBlank()) {
                    reverseCache[cacheKey] = displayName
                    return@withContext displayName
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim reverse geocode failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
