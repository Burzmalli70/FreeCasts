package dev.josephwilliams.freecasts.ui.screens.settings

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.josephwilliams.freecasts.data.export.PODCASTS_EXPORT_FILENAME
import dev.josephwilliams.freecasts.data.preferences.SKIP_INTERVAL_OPTIONS_SECONDS
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExportAfterPermission by remember { mutableStateOf(false) }
    var pendingImportAfterPermission by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onStoragePermissionResult(
            granted = granted,
            isForExport = pendingExportAfterPermission
        )
        pendingExportAfterPermission = false
        pendingImportAfterPermission = false
    }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportToSelectedUri(it) }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromSelectedUri(it) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.RequestStoragePermission -> {
                    if (event.isForExport) {
                        pendingExportAfterPermission = true
                    } else {
                        pendingImportAfterPermission = true
                    }
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                SettingsEvent.PickExportLocation -> {
                    exportDocumentLauncher.launch(PODCASTS_EXPORT_FILENAME)
                }
                SettingsEvent.PickImportFile -> {
                    importDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                is SettingsEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Subscriptions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                title = "Auto-download latest episode",
                description = "Automatically download the most recent episode when subscribing to a new podcast",
                checked = state.autoDownloadOnSubscribe,
                onCheckedChange = { viewModel.setAutoDownloadOnSubscribe(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                title = "Delete played episode downloads",
                description = "Delete downloaded episodes once they've been played",
                checked = state.deletePlayedDownloads,
                onCheckedChange = { viewModel.setDeletePlayedDownloads(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                title = "Keep downloaded favorite episodes",
                description = "Favorite episodes remain downloaded after they are played",
                checked = state.keepFavoriteDownloads,
                onCheckedChange = { viewModel.setKeepFavoriteDownloads(it) },
                enabled = state.deletePlayedDownloads
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Playback",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            SkipIntervalSetting(
                title = "Skip forward interval",
                description = "How far the skip forward button moves in the player",
                selectedSeconds = state.skipForwardIntervalSeconds,
                onSelected = { viewModel.setSkipForwardIntervalSeconds(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SkipIntervalSetting(
                title = "Skip back interval",
                description = "How far the skip back button moves in the player",
                selectedSeconds = state.skipBackwardIntervalSeconds,
                onSelected = { viewModel.setSkipBackwardIntervalSeconds(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitch(
                title = "External prev/next uses skip intervals",
                description = "When enabled, Bluetooth remotes, lock screen, and notification " +
                    "prev/next buttons skip by your configured interval instead of changing " +
                    "tracks. When disabled, they move to the previous or next episode in a playlist.",
                checked = state.externalPrevNextUsesSkipIntervals,
                onCheckedChange = { viewModel.setExternalPrevNextUsesSkipIntervals(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Export or import your subscriptions, playlists, app settings, per-podcast download " +
                    "preferences, favorites, played episodes, and playback positions as " +
                    "$PODCASTS_EXPORT_FILENAME. The app saves to Documents by default, or lets " +
                    "you choose a location if needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            state.transferProgress?.let { progress ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = progress.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.exportSubscriptions() },
                    enabled = !state.isExporting && !state.isImporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.isExporting) "Exporting…" else "Export backup")
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(
                    onClick = { viewModel.importSubscriptions() },
                    enabled = !state.isExporting && !state.isImporting,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.isImporting) "Importing…" else "Import backup")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkipIntervalSetting(
    title: String,
    description: String,
    selectedSeconds: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SKIP_INTERVAL_OPTIONS_SECONDS.forEach { seconds ->
                FilterChip(
                    selected = selectedSeconds == seconds,
                    onClick = { onSelected(seconds) },
                    label = { Text("${seconds}s") }
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
