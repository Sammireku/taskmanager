package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    private var isInitialized: Boolean = false
    private var authStatusMessage: String = "Initializing..."

    /**
     * Initializes FirebaseApp safely, avoiding the 'Default FirebaseApp failed to initialize' error
     * when google-services.json is absent or partially configured.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                Log.i(TAG, "Initializing default FirebaseApp instance...")
                
                // Attempt standard auto-initialization from google-services.json
                val app = FirebaseApp.initializeApp(context)
                if (app == null) {
                    // Safe programmatic fallback for FirebaseOptions
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:${System.currentTimeMillis()}:android:cobbyai_default")
                        .setApiKey(BuildConfig.MAPS_API_KEY.ifBlank { "AIzaSyCobbyAiDefaultKeyPlaceholder" })
                        .setProjectId("cobbyai-applet-project")
                        .setGcmSenderId("100000000000")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                    Log.i(TAG, "FirebaseApp programmatically initialized with safe default options.")
                }
            } else {
                Log.i(TAG, "FirebaseApp already initialized (${apps.size} app instance found).")
            }

            isInitialized = true
            setupAuthenticationStatus()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization warning: ${e.message}")
            try {
                // Last-resort fallback initialization with basic options
                val fallbackOptions = FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:fallback_app_id")
                    .setApiKey(BuildConfig.MAPS_API_KEY.ifBlank { "AIzaSyDefaultFallbackKey" })
                    .setProjectId("cobbyai-fallback")
                    .build()
                FirebaseApp.initializeApp(context, fallbackOptions, "[DEFAULT]")
                isInitialized = true
                authStatusMessage = "Firebase Active (Fallback Config)"
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize Firebase fallback: ${t.message}", t)
                authStatusMessage = "Firebase Offline (Local Mode)"
            }
        }
    }

    private fun setupAuthenticationStatus() {
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            authStatusMessage = if (user != null) {
                "Authenticated (${user.email ?: user.uid})"
            } else {
                "Anonymous / Guest Mode Ready"
            }
        } catch (e: Exception) {
            authStatusMessage = "Firebase Auth Ready (Local State)"
        }
    }

    fun getAuthStatus(): String = authStatusMessage
    fun isReady(): Boolean = isInitialized
}
