package dev.josephwilliams.freecasts.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.download.FavoriteEpisodeDownloadHandler
import dev.josephwilliams.freecasts.data.export.FreeCastsBackupBuilder
import dev.josephwilliams.freecasts.data.export.FreeCastsBackupImportHandler
import dev.josephwilliams.freecasts.data.export.PODCASTS_EXPORT_FILENAME
import dev.josephwilliams.freecasts.data.export.PodcastSubscriptionsFileManager
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
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
    private val backupBuilder: FreeCastsBackupBuilder,
    private val backupImportHandler: FreeCastsBackupImportHandler,
    private val subscriptionsFileManager: PodcastSubscriptionsFileManager,
    private val favoriteEpisodeDownloadHandler: FavoriteEpisodeDownloadHandler
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
                        skipForwardIntervalSeconds = preferences.skipForwardIntervalSeconds,
                        skipBackwardIntervalSeconds = preferences.skipBackwardIntervalSeconds,
                        externalPrevNextUsesSkipIntervals = preferences.externalPrevNextUsesSkipIntervals,
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

    fun setSkipForwardIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSkipForwardIntervalSeconds(seconds)
        }
    }

    fun setSkipBackwardIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setSkipBackwardIntervalSeconds(seconds)
        }
    }

    fun setExternalPrevNextUsesSkipIntervals(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExternalPrevNextUsesSkipIntervals(enabled)
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
            performExport(toUri = uri)
        }
    }

    fun importFromSelectedUri(uri: Uri) {
        viewModelScope.launch {
            performImport(fromUri = uri)
        }
    }

    private suspend fun performExportToDocuments() {
        performExport(toUri = null)
    }

    private suspend fun performImportFromDocuments() {
        performImport(fromUri = null)
    }

    private suspend fun performExport(toUri: Uri?) {
        _state.update {
            it.copy(
                isExporting = true,
                isImporting = false,
                transferProgress = TransferProgress(current = 0, total = 1, label = "Starting export…")
            )
        }

        val backupResult = runCatching {
            backupBuilder.buildBackup { current, total, label ->
                _state.update {
                    it.copy(transferProgress = TransferProgress(current, total, label))
                }
            }
        }

        val backup = backupResult.getOrNull()
        if (backup == null) {
            _state.update {
                it.copy(isExporting = false, transferProgress = null)
            }
            _events.emit(
                SettingsEvent.ShowMessage(
                    "Export failed: ${backupResult.exceptionOrNull()?.message ?: "Unknown error"}"
                )
            )
            return
        }

        _state.update {
            it.copy(transferProgress = TransferProgress(1, 1, "Writing backup file…"))
        }

        val writeResult = if (toUri != null) {
            subscriptionsFileManager.exportToUri(toUri, backup)
        } else {
            subscriptionsFileManager.exportBackup(backup)
        }

        _state.update {
            it.copy(isExporting = false, transferProgress = null)
        }

        writeResult.fold(
            onSuccess = {
                val episodeStateCount = backup.episodeStates.size
                val playlistCount = backup.playlists.size
                val message = buildString {
                    append("Exported ${backup.podcasts.size} podcast${if (backup.podcasts.size == 1) "" else "s"}")
                    if (playlistCount > 0) {
                        append(", $playlistCount playlist${if (playlistCount == 1) "" else "s"}")
                    }
                    if (episodeStateCount > 0) {
                        append(" and $episodeStateCount episode state${if (episodeStateCount == 1) "" else "s"}")
                    }
                    if (toUri == null) {
                        append(" to Documents/$PODCASTS_EXPORT_FILENAME")
                    }
                }
                _events.emit(SettingsEvent.ShowMessage(message))
            },
            onFailure = { error ->
                if (toUri == null) {
                    _events.emit(SettingsEvent.PickExportLocation)
                } else {
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            "Export failed: ${error.message ?: "Unknown error"}"
                        )
                    )
                }
            }
        )
    }

    private suspend fun performImport(fromUri: Uri?) {
        _state.update {
            it.copy(
                isImporting = true,
                isExporting = false,
                transferProgress = TransferProgress(current = 0, total = 1, label = "Reading backup file…")
            )
        }

        val readResult = if (fromUri != null) {
            subscriptionsFileManager.importFromUri(fromUri)
        } else {
            subscriptionsFileManager.importBackup()
        }

        val backup = readResult.getOrNull()
        if (backup == null) {
            _state.update {
                it.copy(isImporting = false, transferProgress = null)
            }
            if (fromUri == null) {
                _events.emit(SettingsEvent.PickImportFile)
            } else {
                _events.emit(
                    SettingsEvent.ShowMessage(
                        "Import failed: ${readResult.exceptionOrNull()?.message ?: "Unknown error"}"
                    )
                )
            }
            return
        }

        val importResult = runCatching {
            backupImportHandler.importBackup(backup) { current, total, label ->
                _state.update {
                    it.copy(transferProgress = TransferProgress(current, total, label))
                }
            }
        }

        importResult.fold(
            onSuccess = { result ->
                val favoriteDownloadsQueued = queueFavoriteDownloadsAfterImport()
                val message = buildString {
                    append(result.toMessage())
                    if (favoriteDownloadsQueued > 0) {
                        append(", queued $favoriteDownloadsQueued favorite episode download")
                        if (favoriteDownloadsQueued != 1) append("s")
                    }
                }
                _events.emit(SettingsEvent.ShowMessage(message))
            },
            onFailure = { error ->
                _events.emit(
                    SettingsEvent.ShowMessage(
                        "Import failed: ${error.message ?: "Unknown error"}"
                    )
                )
            }
        )

        _state.update {
            it.copy(isImporting = false, transferProgress = null)
        }
    }

    private suspend fun queueFavoriteDownloadsAfterImport(): Int {
        _state.update {
            it.copy(transferProgress = TransferProgress(0, 1, "Queueing favorite downloads…"))
        }
        favoriteEpisodeDownloadHandler.markFavoriteDownloadsPendingAfterImport()
        return favoriteEpisodeDownloadHandler.enqueueDownloadsForAllFavorites { current, total, label ->
            _state.update {
                it.copy(transferProgress = TransferProgress(current, total, label))
            }
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
    val skipForwardIntervalSeconds: Int = 30,
    val skipBackwardIntervalSeconds: Int = 30,
    val externalPrevNextUsesSkipIntervals: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val transferProgress: TransferProgress? = null
)

data class TransferProgress(
    val current: Int,
    val total: Int,
    val label: String
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
}

sealed class SettingsEvent {
    data class RequestStoragePermission(val isForExport: Boolean) : SettingsEvent()
    data object PickExportLocation : SettingsEvent()
    data object PickImportFile : SettingsEvent()
    data class ShowMessage(val message: String) : SettingsEvent()
}
