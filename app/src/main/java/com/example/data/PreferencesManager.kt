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
        private const val KEY_TERMS_ACCEPTED = "key_terms_accepted"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_VOICE_GENDER = "key_voice_gender" // "MALE", "FEMALE", "DEFAULT"
        private const val KEY_VOICE_PITCH = "key_voice_pitch" // Float, default 0.95f
        private const val KEY_VOICE_RATE = "key_voice_rate" // Float, default 0.95f
    }

    var isTermsAccepted: Boolean
        get() = prefs.getBoolean(KEY_TERMS_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var voiceGender: String
        get() = prefs.getString(KEY_VOICE_GENDER, "MALE") ?: "MALE"
        set(value) = prefs.edit().putString(KEY_VOICE_GENDER, value).apply()

    var voicePitch: Float
        get() = prefs.getFloat(KEY_VOICE_PITCH, 0.95f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_PITCH, value).apply()

    var voiceRate: Float
        get() = prefs.getFloat(KEY_VOICE_RATE, 0.95f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_RATE, value).apply()

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
