package dev.josephwilliams.freecasts.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 */
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        observePreferences()
    }
    
    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { preferences ->
                _state.update { it.copy(
                    autoDownloadOnSubscribe = preferences.autoDownloadOnSubscribe,
                    keepFavoriteDownloads = preferences.keepFavoriteDownloads,
                    deletePlayedDownloads = preferences.deletePlayedDownloads,
                    isLoading = false
                )}
            }
        }
    }
    
    /**
     * Toggle the auto-download on subscribe setting.
     */
    fun setAutoDownloadOnSubscribe(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoDownloadOnSubscribe(enabled)
        }
    }

    fun setKeepFavoriteDownloads(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepFavoriteDownloads(enabled)
        }
    }

    fun setDeletePlayedDownloads(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDeletePlayedDownloads(enabled)
        }
    }
}

/**
 * State for the settings screen.
 */
data class SettingsState(
    val autoDownloadOnSubscribe: Boolean = false,
    val keepFavoriteDownloads: Boolean = false,
    val deletePlayedDownloads: Boolean = false,
    val isLoading: Boolean = true
)
