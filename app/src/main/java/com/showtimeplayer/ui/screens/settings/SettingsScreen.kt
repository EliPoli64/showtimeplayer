package com.showtimeplayer.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permission = audioPermission()
    var pendingFolderPicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingFolderPicker = true
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let { viewModel.addFolder(it.toString()) }
    }

    LaunchedEffect(pendingFolderPicker) {
        if (pendingFolderPicker) {
            pendingFolderPicker = false
            folderPicker.launch(null)
        }
    }

    LaunchedEffect(uiState.scanResultMessage) {
        uiState.scanResultMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissScanResult()
        }
    }

    fun onAddFolder() {
        val hasPermission = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            folderPicker.launch(null)
        } else {
            pendingFolderPicker = false
            permissionLauncher.launch(permission)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Music Folders",
                style = MaterialTheme.typography.titleMedium,
            )

            if (uiState.folders.isEmpty()) {
                Text(
                    text = "Please select at least one folder to scan for music.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = uiState.folders,
                        key = { it.uri },
                    ) { folder ->
                        FolderItem(
                            folder = folder,
                            onRemove = { viewModel.removeFolder(folder.uri) },
                        )
                    }
                }
            }

            Button(
                onClick = ::onAddFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add Folder")
            }

            if (uiState.folders.isNotEmpty()) {
                OutlinedButton(
                    onClick = viewModel::rescan,
                    enabled = !uiState.isScanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text("Scan Now")
                }
            }
        }
    }
}
