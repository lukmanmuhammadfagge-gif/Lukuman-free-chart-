package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lukuman_free_chat_prefs")

enum class AppLanguage(val code: String, val label: String) {
    HAUSA("ha", "Hausa"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: HAUSA
    }
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

class AppPreferences(private val context: Context) {
    private val languageKey = stringPreferencesKey("app_language")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")

    val languageFlow: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[languageKey])
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeModeKey])
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[notificationsEnabledKey] ?: true
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[languageKey] = language.code
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.key
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[notificationsEnabledKey] = enabled
        }
    }
}
