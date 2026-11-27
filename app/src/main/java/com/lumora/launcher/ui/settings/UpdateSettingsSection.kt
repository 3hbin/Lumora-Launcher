package com.lumora.launcher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumora.launcher.LumoraApp
import com.lumora.launcher.update.ApkInstaller
import com.lumora.launcher.update.UpdateState
import com.lumora.launcher.update.UpdateUiState
import kotlinx.coroutines.launch

@Composable
fun UpdateSettingsSection() {
    val context = LocalContext.current
    val updateRepo = remember { (context.applicationContext as LumoraApp).updateRepository }
    val uiState by updateRepo.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "App Updates",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))

        // Current version
        ListItem(
            headlineContent = { Text("Current version") },
            supportingContent = {
                Text("${uiState.currentVersionName} (${uiState.currentVersionCode})")
            },
            leadingContent = {
                Icon(Icons.Default.SystemUpdate, contentDescription = null)
            }
        )

        // Auto check toggle
        ListItem(
            headlineContent = { Text("Check for updates daily") },
            supportingContent = { Text("Uses GitHub Releases · low battery impact") },
            trailingContent = {
                Switch(
                    checked = uiState.autoCheckDaily,
                    onCheckedChange = { enabled ->
                        updateRepo.setAutoCheckDaily(enabled)
                    }
                )
            }
        )

        // Status card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(Modifier = Modifier.padding(16.dp)) {
                when (uiState.state) {
                    UpdateState.IDLE -> {
                        Text("Tap “Check for updates” to look for a new version.")
                    }
                    UpdateState.CHECKING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Checking GitHub Releases…")
                        }
                    }
                    UpdateState.UP_TO_DATE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("You are on the latest version")
                        }
                    }
                    UpdateState.UPDATE_AVAILABLE -> {
                        val latest = uiState.latest
                        Text(
                            text = "Update available: ${latest?.versionName ?: "?"} (${latest?.versionCode ?: "?"})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (!latest?.body.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = latest!!.body.take(600) + if (latest.body.length > 600) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (latest?.publishedAt != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Published: ${latest.publishedAt.take(10)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    UpdateState.DOWNLOADING -> {
                        Text("Downloading… ${(uiState.downloadProgress * 100).toInt()}%")
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = formatBytes(uiState.downloadedBytes) +
                                    if (uiState.latest?.apkSizeBytes ?: 0 > 0)
                                        " / ${formatBytes(uiState.latest!!.apkSizeBytes)}"
                                    else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    UpdateState.DOWNLOADED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Download complete – ready to install")
                        }
                    }
                    UpdateState.INSTALLING -> {
                        Text("Opening system installer…")
                    }
                    UpdateState.ERROR -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = uiState.errorMessage ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch { updateRepo.checkForUpdate() }
                },
                enabled = uiState.state != UpdateState.CHECKING && uiState.state != UpdateState.DOWNLOADING
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Check for updates")
            }

            if (uiState.state == UpdateState.UPDATE_AVAILABLE && uiState.latest?.apkDownloadUrl != null) {
                Button(
                    onClick = {
                        scope.launch {
                            val result = updateRepo.downloadApk(uiState.latest!!)
                            result.onSuccess { file ->
                                downloadedFile = file
                            }
                        }
                    }
                ) {
                    Text("Download")
                }
            }

            if (uiState.state == UpdateState.DOWNLOADED && downloadedFile != null) {
                Button(
                    onClick = {
                        val file = downloadedFile ?: return@Button
                        if (!ApkInstaller.canRequestInstall(context)) {
                            ApkInstaller.openInstallPermissionSettings(context)
                        } else {
                            ApkInstaller.install(context, file)
                        }
                    }
                ) {
                    Text("Install")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Source: github.com/3hbin/Lumora-Launcher/releases",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
