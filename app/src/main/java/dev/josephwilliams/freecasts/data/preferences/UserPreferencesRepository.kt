package dev.josephwilliams.freecasts.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Repository for managing user preferences using DataStore.
 */
class UserPreferencesRepository(
    private val context: Context
) {
    private object PreferencesKeys {
        val AUTO_DOWNLOAD_ON_SUBSCRIBE = booleanPreferencesKey("auto_download_on_subscribe")
    }
    
    /**
     * User preferences data class.
     */
    data class UserPreferences(
        val autoDownloadOnSubscribe: Boolean = false
    )
    
    /**
     * Flow of user preferences.
     */
    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            autoDownloadOnSubscribe = preferences[PreferencesKeys.AUTO_DOWNLOAD_ON_SUBSCRIBE] ?: false
        )
    }
    
    /**
     * Flow for auto-download on subscribe setting.
     */
    val autoDownloadOnSubscribe: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_DOWNLOAD_ON_SUBSCRIBE] ?: false
    }
    
    /**
     * Set whether to auto-download the latest episode when subscribing.
     */
    suspend fun setAutoDownloadOnSubscribe(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_DOWNLOAD_ON_SUBSCRIBE] = enabled
        }
    }
}
