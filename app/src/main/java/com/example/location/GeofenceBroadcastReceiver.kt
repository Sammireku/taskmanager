package com.example.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.work.GeofenceWorkManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val CHANNEL_ID = "proximity_task_channel_alarm_v2"
        const val CHANNEL_NAME = "Proximity Task Alarm Alerts"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "GeofenceBroadcastReceiver received null GeofencingEvent from intent")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing event error code: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        val transitionName = when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN ($geofenceTransition)"
        }
        Log.i(TAG, "Geofence event triggered! Transition: $transitionName, geofences count: ${triggeringGeofences.size}")

        val firstGeofence = triggeringGeofences.firstOrNull()
        val requestId = firstGeofence?.requestId ?: intent.getIntExtra(GeofenceManager.EXTRA_TASK_ID, 0).let { if (it != 0) "task_$it" else "" }

        val triggeringLoc = geofencingEvent.triggeringLocation
        val lat = triggeringLoc?.latitude ?: 0.0
        val lng = triggeringLoc?.longitude ?: 0.0

        // Delegate background processing to WorkManager for battery efficiency and Android 12+ compliance
        GeofenceWorkManager.enqueueGeofenceEvent(
            context = context,
            requestId = requestId,
            transitionType = geofenceTransition,
            latitude = lat,
            longitude = lng
        )
    }
}
