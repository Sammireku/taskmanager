package com.example.places

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
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

    suspend fun getAutocompletePredictions(
        query: String,
        biasCenter: LatLng? = null,
        radiusMeters: Double = 40000.0
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val client = placesClient

        if (client == null) {
            return@withContext InternetGeocodingService.searchPlacesInternet(query, biasCenter)
        }

        val googleResults: List<PlaceSuggestion> = suspendCancellableCoroutine { continuation ->
            try {
                val builder = FindAutocompletePredictionsRequest.builder()
                    .setQuery(query)

                if (biasCenter != null) {
                    try {
                        val latOffset = radiusMeters / 111000.0
                        val cosLat = Math.cos(Math.toRadians(biasCenter.latitude)).let { if (it == 0.0) 0.0001 else it }
                        val lngOffset = radiusMeters / (111000.0 * Math.abs(cosLat))
                        val southWest = LatLng(
                            (biasCenter.latitude - latOffset).coerceIn(-90.0, 90.0),
                            (biasCenter.longitude - lngOffset).coerceIn(-180.0, 180.0)
                        )
                        val northEast = LatLng(
                            (biasCenter.latitude + latOffset).coerceIn(-90.0, 90.0),
                            (biasCenter.longitude + lngOffset).coerceIn(-180.0, 180.0)
                        )
                        val bounds = RectangularBounds.newInstance(southWest, northEast)
                        builder.setLocationBias(bounds)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to apply location bias: ${t.message}")
                    }
                }

                val request = builder.build()

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
            } catch (t: Throwable) {
                Log.e(TAG, "Error initiating autocomplete prediction query", t)
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }

        if (googleResults.isNotEmpty()) {
            googleResults
        } else {
            InternetGeocodingService.searchPlacesInternet(query, biasCenter)
        }
    }

    suspend fun getCandidatePlaces(
        query: String,
        biasCenter: LatLng? = null,
        maxCandidates: Int = 3
    ): List<PlaceDetails> = withContext(Dispatchers.IO) {
        val suggestions = getAutocompletePredictions(query, biasCenter).take(maxCandidates)
        val detailsList = mutableListOf<PlaceDetails>()
        for (suggestion in suggestions) {
            val details = fetchPlaceDetails(suggestion.placeId)
            if (details != null && details.latLng != null) {
                detailsList.add(details)
            }
        }
        detailsList
    }

    suspend fun fetchPlaceDetails(placeId: String): PlaceDetails? = withContext(Dispatchers.IO) {
        if (placeId.startsWith("saved_")) {
            val parts = placeId.removePrefix("saved_").split("_")
            val lat = parts.getOrNull(1)?.toDoubleOrNull()
            val lon = parts.getOrNull(2)?.toDoubleOrNull()
            if (lat != null && lon != null) {
                return@withContext PlaceDetails(
                    placeId = placeId,
                    name = "Frequent Place",
                    address = "Lat: $lat, Lng: $lon",
                    phoneNumber = null,
                    websiteUri = null,
                    rating = null,
                    latLng = LatLng(lat, lon)
                )
            }
        }

        if (placeId.startsWith("osm_")) {
            val parts = placeId.removePrefix("osm_").split("_")
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) {
                val address = InternetGeocodingService.reverseGeocodeInternet(lat, lon) ?: "Selected Location"
                return@withContext PlaceDetails(
                    placeId = placeId,
                    name = address.split(",").firstOrNull()?.trim() ?: address,
                    address = address,
                    phoneNumber = null,
                    websiteUri = null,
                    rating = null,
                    latLng = LatLng(lat, lon)
                )
            }
        }

        if (placeId.startsWith("geo_")) {
            val parts = placeId.removePrefix("geo_").split("_")
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) {
                return@withContext PlaceDetails(
                    placeId = placeId,
                    name = "Location ($lat, $lon)",
                    address = "Lat: $lat, Lng: $lon",
                    phoneNumber = null,
                    websiteUri = null,
                    rating = null,
                    latLng = LatLng(lat, lon)
                )
            }
        }

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
            try {
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
            } catch (t: Throwable) {
                Log.e(TAG, "Error fetching place details for $placeId", t)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
