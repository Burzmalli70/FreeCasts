package dev.josephwilliams.freecasts.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.josephwilliams.freecasts.data.export.ExportedEpisodeState
import dev.josephwilliams.freecasts.tools.normalizeFeedUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Repository for managing user preferences using DataStore.
 */
class UserPreferencesRepository(
    private val context: Context
) {
    private object PreferencesKeys {
        val AUTO_DOWNLOAD_ON_SUBSCRIBE = booleanPreferencesKey("auto_download_on_subscribe")
        val KEEP_FAVORITE_DOWNLOADS = booleanPreferencesKey("keep_favorite_downloads")
        val DELETE_PLAYED_DOWNLOADS = booleanPreferencesKey("delete_played_downloads")
        val RANDOM_PODCAST_FAVORITE_ID = longPreferencesKey("random_podcast_favorite_id")
        val PENDING_EPISODE_STATES_JSON = stringPreferencesKey("pending_episode_states_json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }
    
    /**
     * User preferences data class.
     */
    data class UserPreferences(
        val autoDownloadOnSubscribe: Boolean = false,
        val keepFavoriteDownloads: Boolean = false,
        val deletePlayedDownloads: Boolean = false,
        val randomPodcastFavoriteId: Long = -1L
    )
    
    /**
     * Flow of user preferences.
     */
    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            autoDownloadOnSubscribe = preferences[PreferencesKeys.AUTO_DOWNLOAD_ON_SUBSCRIBE] ?: false,
            keepFavoriteDownloads = preferences[PreferencesKeys.KEEP_FAVORITE_DOWNLOADS] ?: false,
            deletePlayedDownloads = preferences[PreferencesKeys.DELETE_PLAYED_DOWNLOADS] ?: false,
            randomPodcastFavoriteId = preferences[PreferencesKeys.RANDOM_PODCAST_FAVORITE_ID] ?: -1L
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

    /**
     * Flow for keep favorite episodes downloaded.
     */
    val keepFavoriteDownloads: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.KEEP_FAVORITE_DOWNLOADS] ?: false
    }

    /**
     * Set whether keep favorite episodes downloaded after they've been played.
     */
    suspend fun setKeepFavoriteDownloads(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_FAVORITE_DOWNLOADS] = enabled
        }
    }

    /**
     * Flow for keep favorite episodes downloaded.
     */
    val deletePlayedDownloads: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DELETE_PLAYED_DOWNLOADS] ?: false
    }

    /**
     * Set whether keep favorite episodes downloaded after they've been played.
     */
    suspend fun setDeletePlayedDownloads(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DELETE_PLAYED_DOWNLOADS] = enabled
        }
    }

    /**
     * Flow for keep favorite episodes downloaded.
     */
    val randomPodcastId: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RANDOM_PODCAST_FAVORITE_ID] ?: -1L
    }

    /**
     * Set whether keep favorite episodes downloaded after they've been played.
     */
    suspend fun setRandomPodcastId(podcastId: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RANDOM_PODCAST_FAVORITE_ID] = podcastId
        }
    }

    suspend fun clearRandomPodcastId() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RANDOM_PODCAST_FAVORITE_ID] = -1L
        }
    }

    suspend fun getPendingEpisodeStates(): List<ExportedEpisodeState> {
        val raw = context.dataStore.data.first()[PreferencesKeys.PENDING_EPISODE_STATES_JSON]
            ?: return emptyList()
        return try {
            json.decodeFromString<List<ExportedEpisodeState>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setPendingEpisodeStates(states: List<ExportedEpisodeState>) {
        val deduped = states.distinctBy { it.feedUrl.normalizeFeedUrl() to it.guid }
        context.dataStore.edit { preferences ->
            if (deduped.isEmpty()) {
                preferences.remove(PreferencesKeys.PENDING_EPISODE_STATES_JSON)
            } else {
                preferences[PreferencesKeys.PENDING_EPISODE_STATES_JSON] =
                    json.encodeToString(deduped)
            }
        }
    }

    suspend fun removePendingEpisodeState(feedUrl: String, guid: String) {
        val normalizedFeedUrl = feedUrl.normalizeFeedUrl()
        val updated = getPendingEpisodeStates().filterNot {
            it.feedUrl.normalizeFeedUrl() == normalizedFeedUrl && it.guid == guid
        }
        setPendingEpisodeStates(updated)
    }
}
