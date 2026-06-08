package dev.josephwilliams.freecasts.ui.screens.settings

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
                text = "Backup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Export or import your subscribed podcasts as $PODCASTS_EXPORT_FILENAME. " +
                    "The app saves to Documents by default, or lets you choose a location if needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.exportSubscriptions() },
                    enabled = !state.isExporting && !state.isImporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Export subscriptions")
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(
                    onClick = { viewModel.importSubscriptions() },
                    enabled = !state.isExporting && !state.isImporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Import subscriptions")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        )
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
