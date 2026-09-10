package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_app_settings")

class AppSettingsDataStore(private val context: Context) {

    companion object {
        val KEY_SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback_enabled")
        val KEY_BANNER_NOTIFICATIONS = booleanPreferencesKey("banner_notifications_enabled")
        val KEY_FULLSCREEN_ALARM = booleanPreferencesKey("fullscreen_alarm_enabled")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
    }

    val soundFeedbackFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SOUND_FEEDBACK] ?: true
    }

    val bannerNotificationsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BANNER_NOTIFICATIONS] ?: true
    }

    val fullscreenAlarmFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FULLSCREEN_ALARM] ?: true
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    suspend fun setSoundFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_FEEDBACK] = enabled
        }
    }

    suspend fun setBannerNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BANNER_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setFullscreenAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FULLSCREEN_ALARM] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }
}
