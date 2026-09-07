package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.example.data.Task
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class ProximityTaskInfo(
    val task: Task,
    val distanceMeters: Float,
    val isWithinGeofence: Boolean
)

data class ErrandCluster(
    val taskA: Task,
    val taskB: Task,
    val distanceBetweenMeters: Float
)

class LocationHelper(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationHelper"

        fun calculateDistance(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }

        fun formatDistance(meters: Float): String {
            return if (meters < 1000) {
                "${meters.toInt()}m"
            } else {
                String.format(Locale.getDefault(), "%.1f km", meters / 1000f)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSystemLocationFallback(): Location? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return null
            // Network provider specifically resolves location via Wi-Fi networks and cell towers
            val networkLoc = if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                try { locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
            } else null
            val gpsLoc = if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                try { locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
            } else null
            val passiveLoc = try {
                locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
            } catch (_: Exception) { null }

            listOfNotNull(networkLoc, gpsLoc, passiveLoc).maxByOrNull { it.time }
        } catch (e: Exception) {
            Log.w(TAG, "Failed system location fallback", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            try {
                // Priority.PRIORITY_HIGH_ACCURACY leverages GPS, Wi-Fi, and Cell for optimal fix
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).addOnSuccessListener { loc ->
                    if (loc != null) {
                        if (continuation.isActive) continuation.resume(loc)
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            val resolved = lastLoc ?: getSystemLocationFallback()
                            if (continuation.isActive) continuation.resume(resolved)
                        }.addOnFailureListener {
                            val fallback = getSystemLocationFallback()
                            if (continuation.isActive) continuation.resume(fallback)
                        }
                    }
                }.addOnFailureListener {
                    // Fallback to last known location or system location provider (Wi-Fi/cell)
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        val resolved = lastLoc ?: getSystemLocationFallback()
                        if (continuation.isActive) continuation.resume(resolved)
                    }.addOnFailureListener {
                        val fallback = getSystemLocationFallback()
                        if (continuation.isActive) continuation.resume(fallback)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Location permission missing", e)
                if (continuation.isActive) continuation.resume(null)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get location", e)
                val fallback = getSystemLocationFallback()
                if (continuation.isActive) continuation.resume(fallback)
            }

            continuation.invokeOnCancellation {
                cts.cancel()
            }
        }
    }

    /**
     * Finds tasks near the user's location and calculates distances.
     */
    fun getNearbyTasks(userLocation: Location, tasks: List<Task>): List<ProximityTaskInfo> {
        val tasksWithLocation = tasks.filter { !it.isCompleted && it.latitude != null && it.longitude != null }

        return tasksWithLocation.map { task ->
            val distance = calculateDistance(
                userLocation.latitude,
                userLocation.longitude,
                task.latitude!!,
                task.longitude!!
            )
            ProximityTaskInfo(
                task = task,
                distanceMeters = distance,
                isWithinGeofence = distance <= task.geofenceRadius
            )
        }.sortedBy { it.distanceMeters }
    }

    /**
     * Identifies pending tasks that are physically clustered together (e.g. within 400m).
     */
    fun findClusters(tasks: List<Task>, maxClusterDistanceMeters: Float = 400f): List<ErrandCluster> {
        val pendingWithLoc = tasks.filter { !it.isCompleted && it.latitude != null && it.longitude != null }
        val clusters = mutableListOf<ErrandCluster>()

        for (i in pendingWithLoc.indices) {
            for (j in i + 1 until pendingWithLoc.size) {
                val t1 = pendingWithLoc[i]
                val t2 = pendingWithLoc[j]
                val dist = calculateDistance(t1.latitude!!, t1.longitude!!, t2.latitude!!, t2.longitude!!)
                if (dist <= maxClusterDistanceMeters) {
                    clusters.add(ErrandCluster(t1, t2, dist))
                }
            }
        }
        return clusters
    }

    /**
     * Geocodes a place query or address into latitude, longitude, and friendly name.
     */
    suspend fun searchPlace(query: String): Pair<String, Pair<Double, Double>>? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, 1) { addresses ->
                        val first = addresses.firstOrNull()
                        if (first != null) {
                            val name = first.featureName ?: first.thoroughfare ?: query
                            continuation.resume(Pair(name, Pair(first.latitude, first.longitude)))
                        } else {
                            continuation.resume(null)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val results: List<Address>? = geocoder.getFromLocationName(query, 1)
                val first = results?.firstOrNull()
                if (first != null) {
                    val name = first.featureName ?: first.thoroughfare ?: query
                    Pair(name, Pair(first.latitude, first.longitude))
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder error for query $query", e)
            null
        }
    }

    /**
     * Geocodes a list of matching places for suggestions when querying places or addresses.
     */
    suspend fun searchPlacesList(query: String, maxResults: Int = 5): List<Pair<String, Pair<Double, Double>>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, maxResults) { addresses ->
                        val list = addresses.map { addr ->
                            val mainName = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: query
                            val details = listOfNotNull(addr.subThoroughfare, addr.thoroughfare, addr.locality, addr.adminArea, addr.countryName)
                                .distinct()
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                            val label = if (details.isNotBlank() && !details.equals(mainName, ignoreCase = true)) {
                                "$mainName, $details"
                            } else mainName
                            Pair(label, Pair(addr.latitude, addr.longitude))
                        }
                        if (continuation.isActive) continuation.resume(list)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val results: List<Address>? = geocoder.getFromLocationName(query, maxResults)
                results?.map { addr ->
                    val mainName = addr.featureName ?: addr.thoroughfare ?: addr.locality ?: query
                    val details = listOfNotNull(addr.subThoroughfare, addr.thoroughfare, addr.locality, addr.adminArea, addr.countryName)
                        .distinct()
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    val label = if (details.isNotBlank() && !details.equals(mainName, ignoreCase = true)) {
                        "$mainName, $details"
                    } else mainName
                    Pair(label, Pair(addr.latitude, addr.longitude))
                } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder searchPlacesList error for query $query", e)
            emptyList()
        }
    }

    /**
     * Reverse geocodes coordinates to a place name.
     */
    suspend fun getAddressFromCoordinates(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lng, 1)
            val first = list?.firstOrNull()
            first?.let {
                val parts = listOfNotNull(it.featureName, it.thoroughfare, it.locality, it.adminArea)
                    .distinct()
                    .filter { p -> p.isNotBlank() }
                if (parts.isNotEmpty()) parts.joinToString(", ") else "Selected Location"
            } ?: "Selected Location"
        } catch (e: Exception) {
            "Selected Location"
        }
    }
}
