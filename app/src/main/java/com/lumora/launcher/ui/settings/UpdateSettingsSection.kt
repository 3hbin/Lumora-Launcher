package com.lumora.launcher.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumora.launcher.LumoraApp
import com.lumora.launcher.update.UpdateConfig
import com.lumora.launcher.update.UpdateState
import kotlinx.coroutines.launch

/**
 * Update UI – checks GitHub Releases API for version info only.
 * Does NOT download or install APKs in-app (Play Protect / security).
 * User is directed to the official Releases page in the browser.
 */
@Composable
fun UpdateSettingsSection() {
    val context = LocalContext.current
    val updateRepo = remember { (context.applicationContext as LumoraApp).updateRepository }
    val uiState by updateRepo.uiState.collectAsState()
    val scope = rememberCoroutineScope()

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
            supportingContent = { Text("Only checks version metadata · no background downloads") },
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
                        Text("Tap “Check for updates” to look for a new version on GitHub.")
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
                                text = body.take(500) + if (body.length > 500) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                                text = uiState.errorMessage ?: "Check failed",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        Text("Status: ${uiState.state}")
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
                enabled = uiState.state != UpdateState.CHECKING
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check for updates")
            }

            // Always offer opening the official releases page (safe path)
            Button(
                onClick = {
                    val url = uiState.latest?.htmlUrl ?: UpdateConfig.releasesPageUrl
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (uiState.state == UpdateState.UPDATE_AVAILABLE) "Get update"
                    else "Open releases"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Updates are installed manually from GitHub Releases. Lumora never downloads or installs APKs by itself.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
