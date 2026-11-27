package com.lumora.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumora.launcher.LumoraApp
import com.lumora.launcher.R
import com.lumora.launcher.data.ThemeMode
import com.lumora.launcher.ui.home.LauncherViewModel
import com.lumora.launcher.ui.theme.LumoraTheme
import com.lumora.launcher.ui.settings.UpdateSettingsSection
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as LumoraApp).repository
        setContent {
            val vm: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory(repository, (application as LumoraApp).weatherRepository))
            val settings by vm.settings.collectAsStateWithLifecycle()
            LumoraTheme(themeMode = settings.themeMode, dynamicColor = settings.useDynamicColor) {
                SettingsScreen(
                    viewModel = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showHiddenApps by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Appearance
            item { SectionHeader(stringResource(R.string.appearance)) }
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.theme),
                    subtitle = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    },
                    onClick = {
                        val next = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        viewModel.setThemeMode(next)
                    }
                )
            }
            item {
                SwitchItem(
                    icon = Icons.Default.ColorLens,
                    title = stringResource(R.string.dynamic_color),
                    checked = settings.useDynamicColor,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            // Home Screen
            item { SectionHeader(stringResource(R.string.home_screen)) }
            item {
                SettingsItem(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.grid_columns),
                    subtitle = "${settings.gridColumns} × ${settings.gridRows}",
                    onClick = {
                        val cols = if (settings.gridColumns >= 5) 3 else settings.gridColumns + 1
                        viewModel.setGrid(cols, settings.gridRows)
                    }
                )
            }
            item {
                SwitchItem(
                    icon = Icons.Default.Label,
                    title = stringResource(R.string.show_labels),
                    checked = settings.showLabels,
                    onCheckedChange = { viewModel.updateSettings { s -> s.copy(showLabels = it) } }
                )
            }
            item {
                SwitchItem(
                    icon = Icons.Default.Animation,
                    title = stringResource(R.string.enable_animations),
                    checked = settings.enableAnimations,
                    onCheckedChange = { viewModel.updateSettings { s -> s.copy(enableAnimations = it) } }
                )
            }

            // App Drawer / Hidden
            item { SectionHeader(stringResource(R.string.app_drawer)) }
            item {
                SettingsItem(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.hidden_apps),
                    subtitle = stringResource(R.string.manage_hidden_apps),
                    onClick = { showHiddenApps = true }
                )
            }

            // Clock
            item { SectionHeader(stringResource(R.string.clock_calendar)) }
            item {
                SwitchItem(
                    icon = Icons.Default.Schedule,
                    title = "24-hour clock",
                    checked = settings.clockFormat24h,
                    onCheckedChange = { viewModel.updateSettings { s -> s.copy(clockFormat24h = it) } }
                )
            }

            // Backup
            item { SectionHeader(stringResource(R.string.backup_restore)) }
            item {
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.export_backup),
                    subtitle = "Save layout & settings as JSON",
                    onClick = {
                        scope.launch {
                            try {
                                val json = (context.applicationContext as LumoraApp).repository.exportBackup()
                                val file = (context.applicationContext as LumoraApp).repository.getBackupFile()
                                file.writeText(json)
                                snackbarHostState.showSnackbar(context.getString(R.string.backup_success))
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(context.getString(R.string.backup_failed))
                            }
                        }
                    }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.import_backup),
                    subtitle = "Restore from previous backup",
                    onClick = {
                        scope.launch {
                            try {
                                val file = (context.applicationContext as LumoraApp).repository.getBackupFile()
                                if (file.exists()) {
                                    val ok = (context.applicationContext as LumoraApp).repository.importBackup(file.readText())
                                    snackbarHostState.showSnackbar(
                                        if (ok) context.getString(R.string.restore_success)
                                        else context.getString(R.string.restore_failed)
                                    )
                                    if (ok) viewModel.loadHomeLayout()
                                } else {
                                    snackbarHostState.showSnackbar(context.getString(R.string.restore_failed))
                                }
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(context.getString(R.string.restore_failed))
                            }
                        }
                    }
                )
            }

            // About
            item { SectionHeader("App Updates") }
            item { UpdateSettingsSection() }

            item { SectionHeader(stringResource(R.string.about)) }
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    subtitle = "Lumora Launcher 1.0.0",
                    onClick = { showAbout = true }
                )
            }
        }
    }

    if (showHiddenApps) {
        HiddenAppsDialog(
            viewModel = viewModel,
            onDismiss = { showHiddenApps = false }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_description))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.made_with_love))
                    Spacer(Modifier.height(8.dp))
                    Text("Version 1.0.0")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
fun HiddenAppsDialog(
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var allApps by remember { mutableStateOf<List<com.lumora.launcher.data.AppInfo>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        allApps = (context.applicationContext as LumoraApp).repository.getAllAppsIncludingHidden()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hidden_apps)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(allApps, key = { it.packageName }) { app ->
                    val hidden = app.packageName in settings.hiddenApps
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName) },
                        trailingContent = {
                            Switch(
                                checked = hidden,
                                onCheckedChange = { checked ->
                                    if (checked) viewModel.hideApp(app.packageName)
                                    else viewModel.unhideApp(app.packageName)
                                }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
