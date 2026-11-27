package com.lumora.launcher.ui.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumora.launcher.LumoraApp
import com.lumora.launcher.R
import com.lumora.launcher.data.AppInfo
import com.lumora.launcher.data.HomeItem
import com.lumora.launcher.ui.settings.SettingsActivity
import com.lumora.launcher.ui.theme.LumoraTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModel.Factory((application as LumoraApp).repository, (application as LumoraApp).weatherRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Transparent window so wallpaper shows through
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LumoraTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.useDynamicColor
            ) {
                LauncherScreen(
                    viewModel = viewModel,
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onRequestDefaultLauncher = {
                        try {
                            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshApps()
        viewModel.loadHomeLayout()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.isDrawerOpen.value) {
            viewModel.closeDrawer()
        } else if (viewModel.isEditMode.value) {
            viewModel.setEditMode(false)
        }
        // Otherwise absorb back press (launcher behavior)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    onOpenSettings: () -> Unit,
    onRequestDefaultLauncher: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val homeItems by viewModel.homeItems.collectAsStateWithLifecycle()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()

    val pageCount = maxOf(1, settings.homePages)
    val pagerState = rememberPagerState(initialPage = currentPage, pageCount = { pageCount })

    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentPage(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -40) { // swipe up
                        viewModel.openDrawer()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { viewModel.setEditMode(true) },
                    onDoubleTap = {
                        // Double tap to sleep is optional and requires special privileges;
                        // we intentionally do not force Device Admin.
                    }
                )
            }
    ) {
        // Clock area
        ClockAndStatus(
            format24h = settings.clockFormat24h,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 24.dp)
        )

        // Home pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 96.dp)
        ) { page ->
            HomePageContent(
                page = page,
                items = homeItems.filter { it.page == page },
                apps = apps,
                settings = settings,
                isEditMode = isEditMode,
                onLaunch = { pkg, act -> viewModel.launchApp(pkg, act) },
                onRemove = { viewModel.removeHomeItem(it) }
            )
        }

        // Dock
        DockBar(
            dockKeys = settings.dockApps,
            apps = apps,
            iconSize = settings.iconSizeDp,
            showLabels = settings.showLabels,
            onLaunch = { viewModel.launchApp(it) },
            onOpenDrawer = { viewModel.openDrawer() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
        )

        // Edit mode indicator
        AnimatedVisibility(
            visible = isEditMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            FilledTonalButton(onClick = { viewModel.setEditMode(false) }) {
                Text("Done")
            }
        }

        // App Drawer overlay
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200))
        ) {
            AppDrawer(
                apps = viewModel.filteredApps(),
                searchQuery = searchQuery,
                settings = settings,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onLaunch = { viewModel.launchApp(it) },
                onHide = { viewModel.hideApp(it.packageName) },
                onClose = { viewModel.closeDrawer() },
                onOpenSettings = onOpenSettings,
                onAddToHome = { app ->
                    viewModel.addAppToHome(app, page = pagerState.currentPage)
                    viewModel.closeDrawer()
                }
            )
        }
    }
}

@Composable
fun ClockAndStatus(
    format24h: Boolean,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(format24h) {
        while (true) {
            val now = Date()
            val timePattern = if (format24h) "HH:mm" else "h:mm a"
            time = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
            kotlinx.coroutines.delay(15_000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    blurRadius = 8f
                )
            )
        )
        Text(
            text = date,
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White.copy(alpha = 0.9f),
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.3f),
                    blurRadius = 4f
                )
            )
        )
    }
}

@Composable
fun HomePageContent(
    page: Int,
    items: List<HomeItem>,
    apps: List<AppInfo>,
    settings: com.lumora.launcher.data.LauncherSettings,
    isEditMode: Boolean,
    onLaunch: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    val appMap = remember(apps) { apps.associateBy { it.componentKey } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(settings.gridColumns),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { item ->
            when (item) {
                is HomeItem.AppShortcut -> {
                    val app = appMap["${item.packageName}/${item.activityName}"]
                    if (app != null) {
                        AppIcon(
                            app = app,
                            iconSize = settings.iconSizeDp,
                            showLabel = settings.showLabels,
                            labelSize = settings.labelSizeSp,
                            isEditMode = isEditMode,
                            onClick = { onLaunch(app.packageName, app.activityName) },
                            onLongClick = { if (isEditMode) onRemove(item.id) }
                        )
                    }
                }
                is HomeItem.Folder -> {
                    FolderIcon(
                        folder = item,
                        iconSize = settings.iconSizeDp,
                        showLabel = settings.showLabels,
                        isEditMode = isEditMode,
                        onClick = { /* open folder overlay - simplified */ },
                        onLongClick = { if (isEditMode) onRemove(item.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppInfo,
    iconSize: Int,
    showLabel: Boolean,
    labelSize: Int = 12,
    isEditMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        val bitmap = remember(app.icon) {
            app.icon?.toBitmap(iconSize * 2, iconSize * 2)?.asImageBitmap()
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                )
            }
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = labelSize.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.widthIn(max = (iconSize + 16).dp)
            )
        }
    }
}

@Composable
fun FolderIcon(
    folder: HomeItem.Folder,
    iconSize: Int,
    showLabel: Boolean,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = folder.title,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size((iconSize * 0.55f).dp)
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = folder.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
        }
    }
}

@Composable
fun DockBar(
    dockKeys: List<String>,
    apps: List<AppInfo>,
    iconSize: Int,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockApps = remember(dockKeys, apps) {
        dockKeys.mapNotNull { key ->
            apps.find { it.componentKey == key }
        }.ifEmpty {
            // Sensible defaults: a few common apps if dock empty
            apps.take(4)
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockApps.forEach { app ->
                AppIcon(
                    app = app,
                    iconSize = (iconSize * 0.9f).toInt().coerceAtLeast(40),
                    showLabel = false,
                    onClick = { onLaunch(app) }
                )
            }
            // Drawer button
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(iconSize.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = stringResource(R.string.app_drawer),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    searchQuery: String,
    settings: com.lumora.launcher.data.LauncherSettings,
    onQueryChange: (String) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddToHome: (AppInfo) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Handle + header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_drawer),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(12.dp))

            if (apps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_apps_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.gridColumns),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.componentKey }) { app ->
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            AppIcon(
                                app = app,
                                iconSize = settings.iconSizeDp,
                                showLabel = true,
                                labelSize = settings.labelSizeSp,
                                onClick = { onLaunch(app) },
                                onLongClick = { showMenu = true }
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_to_home)) },
                                    onClick = {
                                        onAddToHome(app)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.hide_app)) },
                                    onClick = {
                                        onHide(app)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.app_info)) },
                                    onClick = {
                                        showMenu = false
                                        // Could open app info intent
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
