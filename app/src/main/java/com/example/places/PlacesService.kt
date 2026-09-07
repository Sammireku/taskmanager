package com.example.places

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class PlaceSuggestion(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String
)

data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String?,
    val phoneNumber: String?,
    val websiteUri: String?,
    val rating: Double?,
    val latLng: LatLng?
)

class PlacesService(private val context: Context) {
    private val TAG = "PlacesService"
    private var placesClient: PlacesClient? = null
    private var isInitialized: Boolean = false

    init {
        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isNotBlank()) {
            try {
                if (!Places.isInitialized()) {
                    Places.initialize(context.applicationContext, apiKey)
                }
                placesClient = Places.createClient(context.applicationContext)
                isInitialized = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize PlacesClient", e)
                isInitialized = false
            }
        } else {
            Log.w(TAG, "MAPS_API_KEY is empty; Places SDK running in fallback mode")
            isInitialized = false
        }
    }

    suspend fun getAutocompletePredictions(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val client = placesClient ?: return@withContext emptyList()

        suspendCancellableCoroutine { continuation ->
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build()

            client.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    val suggestions = response.autocompletePredictions.map { prediction ->
                        PlaceSuggestion(
                            placeId = prediction.placeId,
                            primaryText = prediction.getPrimaryText(null).toString(),
                            secondaryText = prediction.getSecondaryText(null).toString(),
                            fullText = prediction.getFullText(null).toString()
                        )
                    }
                    if (continuation.isActive) continuation.resume(suggestions)
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "findAutocompletePredictions failed: ${exception.message}")
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        }
    }

    suspend fun fetchPlaceDetails(placeId: String): PlaceDetails? = withContext(Dispatchers.IO) {
        val client = placesClient ?: return@withContext null

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.RATING,
            Place.Field.LAT_LNG
        )

        suspendCancellableCoroutine { continuation ->
            val request = FetchPlaceRequest.builder(placeId, placeFields).build()

            client.fetchPlace(request)
                .addOnSuccessListener { response ->
                    val place = response.place
                    val details = PlaceDetails(
                        placeId = place.id ?: placeId,
                        name = place.name ?: "",
                        address = place.address,
                        phoneNumber = place.phoneNumber,
                        websiteUri = place.websiteUri?.toString(),
                        rating = place.rating,
                        latLng = place.latLng
                    )
                    if (continuation.isActive) continuation.resume(details)
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "fetchPlaceDetails failed: ${exception.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }
}
