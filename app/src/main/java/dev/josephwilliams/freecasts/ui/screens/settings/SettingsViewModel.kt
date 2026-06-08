package dev.josephwilliams.freecasts.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.export.ExportedPodcast
import dev.josephwilliams.freecasts.data.export.PodcastSubscriptionsFileManager
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import dev.josephwilliams.freecasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 */
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val podcastDao: PodcastDao,
    private val podcastRepository: PodcastRepository,
    private val subscriptionsFileManager: PodcastSubscriptionsFileManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.userPreferences.collect { preferences ->
                _state.update {
                    it.copy(
                        autoDownloadOnSubscribe = preferences.autoDownloadOnSubscribe,
                        keepFavoriteDownloads = preferences.keepFavoriteDownloads,
                        deletePlayedDownloads = preferences.deletePlayedDownloads,
                        isLoading = false
                    )
                }
            }
        }
    }

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

    fun exportSubscriptions() {
        viewModelScope.launch {
            if (subscriptionsFileManager.needsLegacyStoragePermission() &&
                !subscriptionsFileManager.hasLegacyStoragePermission()
            ) {
                _events.emit(SettingsEvent.RequestStoragePermission(isForExport = true))
                return@launch
            }

            performExportToDocuments()
        }
    }

    fun importSubscriptions() {
        viewModelScope.launch {
            if (subscriptionsFileManager.needsLegacyStoragePermission() &&
                !subscriptionsFileManager.hasLegacyStoragePermission()
            ) {
                _events.emit(SettingsEvent.RequestStoragePermission(isForExport = false))
                return@launch
            }

            performImportFromDocuments()
        }
    }

    fun onStoragePermissionResult(granted: Boolean, isForExport: Boolean) {
        if (!granted) {
            viewModelScope.launch {
                if (isForExport) {
                    _events.emit(SettingsEvent.PickExportLocation)
                } else {
                    _events.emit(SettingsEvent.PickImportFile)
                }
            }
            return
        }

        viewModelScope.launch {
            if (isForExport) {
                performExportToDocuments()
            } else {
                performImportFromDocuments()
            }
        }
    }

    fun exportToSelectedUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true) }

            val podcasts = podcastDao.getSubscribed()
            val result = subscriptionsFileManager.exportToUri(uri, podcasts)

            _state.update { it.copy(isExporting = false) }

            result.fold(
                onSuccess = {
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Exported ${podcasts.size} podcast${if (podcasts.size == 1) "" else "s"}"
                        )
                    )
                },
                onFailure = { error ->
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Export failed: ${error.message ?: "Unknown error"}"
                        )
                    )
                }
            )
        }
    }

    fun importFromSelectedUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }

            val result = subscriptionsFileManager.importFromUri(uri)
            result.fold(
                onSuccess = { podcasts ->
                    importPodcasts(podcasts)
                },
                onFailure = { error ->
                    _state.update { it.copy(isImporting = false) }
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Import failed: ${error.message ?: "Unknown error"}"
                        )
                    )
                }
            )
        }
    }

    private suspend fun performExportToDocuments() {
        _state.update { it.copy(isExporting = true) }

        val podcasts = podcastDao.getSubscribed()
        val result = subscriptionsFileManager.exportPodcasts(podcasts)

        _state.update { it.copy(isExporting = false) }

        result.fold(
            onSuccess = {
                _events.emit(
                    SettingsEvent.ShowMessage(
                        "Exported ${podcasts.size} podcast${if (podcasts.size == 1) "" else "s"} to Documents/$PODCASTS_EXPORT_FILENAME"
                    )
                )
            },
            onFailure = {
                _events.emit(SettingsEvent.PickExportLocation)
            }
        )
    }

    private suspend fun performImportFromDocuments() {
        _state.update { it.copy(isImporting = true) }

        val result = subscriptionsFileManager.importPodcasts()
        result.fold(
            onSuccess = { podcasts ->
                importPodcasts(podcasts)
            },
            onFailure = {
                _state.update { it.copy(isImporting = false) }
                _events.emit(SettingsEvent.PickImportFile)
            }
        )
    }

    private suspend fun importPodcasts(exportedPodcasts: List<ExportedPodcast>) {
        var importedCount = 0
        var skippedCount = 0
        var failedCount = 0

        for (exportedPodcast in exportedPodcasts) {
            if (exportedPodcast.feedUrl.isBlank()) {
                failedCount++
                continue
            }

            val existing = podcastDao.getByFeedUrl(exportedPodcast.feedUrl)
            if (existing?.isSubscribed == true) {
                skippedCount++
                continue
            }

            val subscribeResult = podcastRepository.subscribeToPodcast(exportedPodcast.feedUrl)
            if (subscribeResult.isSuccess) {
                importedCount++
            } else {
                failedCount++
            }
        }

        _state.update { it.copy(isImporting = false) }

        val message = buildString {
            append("Imported $importedCount podcast${if (importedCount == 1) "" else "s"}")
            if (skippedCount > 0) {
                append(", skipped $skippedCount already subscribed")
            }
            if (failedCount > 0) {
                append(", $failedCount failed")
            }
        }
        _events.emit(SettingsEvent.ShowMessage(message))
    }
}

private const val PODCASTS_EXPORT_FILENAME = "podcasts.json"

/**
 * State for the settings screen.
 */
data class SettingsState(
    val autoDownloadOnSubscribe: Boolean = false,
    val keepFavoriteDownloads: Boolean = false,
    val deletePlayedDownloads: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false
)

sealed class SettingsEvent {
    data class RequestStoragePermission(val isForExport: Boolean) : SettingsEvent()
    data object PickExportLocation : SettingsEvent()
    data object PickImportFile : SettingsEvent()
    data class ShowMessage(val message: String) : SettingsEvent()
}
