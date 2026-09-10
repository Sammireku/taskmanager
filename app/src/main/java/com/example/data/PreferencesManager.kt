package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cobby_app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SOUND_FEEDBACK = "key_sound_feedback"
        private const val KEY_BANNER_NOTIFICATIONS = "key_banner_notifications"
        private const val KEY_FULLSCREEN_ALARM = "key_fullscreen_alarm"
        private const val KEY_THEME_MODE = "key_theme_mode" // "SYSTEM", "LIGHT", "DARK"
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_INTERNET_LOCATION = "key_internet_location"
        private const val KEY_INTERNET_LOCATION_CONSENT = "key_internet_location_consent"
        private const val KEY_CLOUD_SYNC_ENABLED = "key_cloud_sync_enabled"
        private const val KEY_USER_NAME = "key_user_name"
    }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var isSoundFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_FEEDBACK, value).apply()

    var isBannerNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BANNER_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_BANNER_NOTIFICATIONS, value).apply()

    var isFullscreenAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN_ALARM, true)
        set(value) = prefs.edit().putBoolean(KEY_FULLSCREEN_ALARM, value).apply()

    var themeModeString: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    var isInternetLocationEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTERNET_LOCATION, true)
        set(value) = prefs.edit().putBoolean(KEY_INTERNET_LOCATION, value).apply()

    var isInternetLocationConsentAsked: Boolean
        get() = prefs.getBoolean(KEY_INTERNET_LOCATION_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_INTERNET_LOCATION_CONSENT, value).apply()

    var isCloudSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, value).apply()
}
