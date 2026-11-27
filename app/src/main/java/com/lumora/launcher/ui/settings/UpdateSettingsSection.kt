package com.lumora.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumora.launcher.LumoraApp
import com.lumora.launcher.update.ApkInstaller
import com.lumora.launcher.update.UpdateState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateSettingsSection() {
    val context = LocalContext.current
    val updateRepo = remember { (context.applicationContext as LumoraApp).updateRepository }
    val uiState by updateRepo.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var downloadedFile by remember { mutableStateOf<File?>(null) }

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
        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Current version") },
            supportingContent = {
                Text("${uiState.currentVersionName} (${uiState.currentVersionCode})")
            },
            leadingContent = {
                Icon(Icons.Default.SystemUpdate, contentDescription = null)
            }
        )

        ListItem(
            headlineContent = { Text("Check for updates daily") },
            supportingContent = { Text("Uses GitHub Releases · low battery impact") },
            trailingContent = {
                Switch(
                    checked = uiState.autoCheckDaily,
                    onCheckedChange = { enabled -> updateRepo.setAutoCheckDaily(enabled) }
                )
            }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (uiState.state) {
                    UpdateState.IDLE -> {
                        Text("Tap “Check for updates” to look for a new version.")
                    }
                    UpdateState.CHECKING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Checking GitHub Releases…")
                        }
                    }
                    UpdateState.UP_TO_DATE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("You are on the latest version")
                        }
                    }
                    UpdateState.UPDATE_AVAILABLE -> {
                        val latest = uiState.latest
                        Text(
                            text = "Update available: ${latest?.versionName ?: "?"} (${latest?.versionCode ?: "?"})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val body = latest?.body
                        if (!body.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = body.take(600) + if (body.length > 600) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val published = latest?.publishedAt
                        if (published != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Published: ${published.take(10)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    UpdateState.DOWNLOADING -> {
                        Text("Downloading… ${(uiState.downloadProgress * 100).toInt()}%")
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val total = uiState.latest?.apkSizeBytes ?: 0L
                        val sizeText = if (total > 0) {
                            "${formatBytes(uiState.downloadedBytes)} / ${formatBytes(total)}"
                        } else {
                            formatBytes(uiState.downloadedBytes)
                        }
                        Text(text = sizeText, style = MaterialTheme.typography.labelSmall)
                    }
                    UpdateState.DOWNLOADED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Download complete – ready to install")
                        }
                    }
                    UpdateState.INSTALLING -> {
                        Text("Opening system installer…")
                    }
                    UpdateState.ERROR -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.errorMessage ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { scope.launch { updateRepo.checkForUpdate() } },
                enabled = uiState.state != UpdateState.CHECKING && uiState.state != UpdateState.DOWNLOADING
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check for updates")
            }

            if (uiState.state == UpdateState.UPDATE_AVAILABLE && uiState.latest?.apkDownloadUrl != null) {
                val latest = uiState.latest
                Button(
                    onClick = {
                        if (latest != null) {
                            scope.launch {
                                updateRepo.downloadApk(latest).onSuccess { file ->
                                    downloadedFile = file
                                }
                            }
                        }
                    }
                ) {
                    Text("Download")
                }
            }

            if (uiState.state == UpdateState.DOWNLOADED && downloadedFile != null) {
                val file = downloadedFile
                Button(
                    onClick = {
                        if (file == null) return@Button
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

        Spacer(modifier = Modifier.height(8.dp))
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
