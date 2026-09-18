package com.example.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.data.Task
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlin.math.roundToInt

@Composable
fun TaskClusterMapView(
    tasks: List<Task>,
    modifier: Modifier = Modifier,
    initialCenter: LatLng? = null,
    onTaskClick: ((Task) -> Unit)? = null,
    onClusterClick: ((TaskCluster) -> Unit)? = null
) {
    val markerItems = remember(tasks) {
        tasks.filter { it.latitude != null && it.longitude != null && !it.isSoftDeleted }
            .map { task ->
                TaskMarkerData(
                    id = task.id,
                    title = task.safeTitle,
                    latitude = task.latitude!!,
                    longitude = task.longitude!!,
                    priority = task.priority,
                    geofenceRadius = task.geofenceRadius,
                    isDone = task.isDone,
                    locationName = task.locationName
                )
            }
    }

    val defaultPos = initialCenter ?: markerItems.firstOrNull()?.latLng ?: LatLng(1.3521, 103.8198)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 14f)
    }

    val zoomLevel = cameraPositionState.position.zoom
    val clusters = remember(markerItems, zoomLevel) {
        MapMarkerClusterer.clusterTasks(markerItems, zoomLevel = zoomLevel)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        clusters.forEach { cluster ->
            if (cluster.isCluster) {
                Marker(
                    state = MarkerState(position = cluster.latLng),
                    title = "📍 ${cluster.count} Clustered Tasks",
                    snippet = cluster.items.take(3).joinToString(", ") { it.title },
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                    onClick = {
                        onClusterClick?.invoke(cluster)
                        false
                    }
                )
            } else {
                val singleTaskData = cluster.items.first()
                val originalTask = tasks.find { it.id == singleTaskData.id }
                val hue = when (singleTaskData.priority.lowercase()) {
                    "high" -> BitmapDescriptorFactory.HUE_RED
                    "low" -> BitmapDescriptorFactory.HUE_GREEN
                    else -> BitmapDescriptorFactory.HUE_ORANGE
                }
                Marker(
                    state = MarkerState(position = cluster.latLng),
                    title = singleTaskData.title,
                    snippet = singleTaskData.locationName ?: "${singleTaskData.geofenceRadius.roundToInt()}m radius",
                    icon = BitmapDescriptorFactory.defaultMarker(hue),
                    onClick = {
                        if (originalTask != null) {
                            onTaskClick?.invoke(originalTask)
                        }
                        false
                    }
                )
                Circle(
                    center = cluster.latLng,
                    radius = singleTaskData.geofenceRadius.toDouble(),
                    fillColor = Color(0x331976D2),
                    strokeColor = Color(0xFF1976D2),
                    strokeWidth = 2f
                )
            }
        }
    }
}
