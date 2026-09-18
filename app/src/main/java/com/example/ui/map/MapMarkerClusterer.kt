package com.example.ui.map

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

data class TaskMarkerData(
    val id: Int,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val priority: String = "Medium",
    val geofenceRadius: Float = 200f,
    val isDone: Boolean = false,
    val locationName: String? = null
) {
    val latLng: LatLng get() = LatLng(latitude, longitude)
}

data class TaskCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val items: List<TaskMarkerData>
) {
    val latLng: LatLng get() = LatLng(latitude, longitude)
    val isCluster: Boolean get() = items.size > 1
    val count: Int get() = items.size
    val primaryTitle: String get() = if (isCluster) "${items.size} Tasks Here" else items.first().title
}

object MapMarkerClusterer {
    /**
     * Thins and clusters map markers based on spatial distance and map zoom level.
     * Prevents UI jank when hundreds of task markers are rendered simultaneously.
     */
    fun clusterTasks(
        tasks: List<TaskMarkerData>,
        zoomLevel: Float = 14f,
        clusterRadiusMeters: Double = 150.0
    ): List<TaskCluster> {
        if (tasks.isEmpty()) return emptyList()

        // Adjust cluster radius dynamically according to zoom level
        // Lower zoom = wider cluster radius
        val effectiveRadiusMeters = when {
            zoomLevel < 10f -> clusterRadiusMeters * 8.0
            zoomLevel < 12f -> clusterRadiusMeters * 4.0
            zoomLevel < 14f -> clusterRadiusMeters * 2.0
            zoomLevel < 16f -> clusterRadiusMeters
            else -> clusterRadiusMeters * 0.4
        }

        val unclustered = tasks.toMutableList()
        val clusters = mutableListOf<TaskCluster>()
        var clusterIdCounter = 1

        while (unclustered.isNotEmpty()) {
            val pivot = unclustered.removeAt(0)
            val clusterItems = mutableListOf(pivot)

            val iterator = unclustered.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                val dist = calculateDistanceMeters(
                    pivot.latitude, pivot.longitude,
                    candidate.latitude, candidate.longitude
                )
                if (dist <= effectiveRadiusMeters) {
                    clusterItems.add(candidate)
                    iterator.remove()
                }
            }

            // Calculate center centroid of cluster
            val avgLat = clusterItems.map { it.latitude }.average()
            val avgLng = clusterItems.map { it.longitude }.average()

            clusters.add(
                TaskCluster(
                    id = "cluster_${clusterIdCounter++}_${clusterItems.size}",
                    latitude = avgLat,
                    longitude = avgLng,
                    items = clusterItems
                )
            )
        }

        return clusters
    }

    private fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
