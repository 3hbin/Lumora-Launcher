package com.lumora.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumora_settings")

class LauncherRepository(private val context: Context) {

    private val gson = Gson()
    private val pm: PackageManager = context.packageManager

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val GRID_ROWS = intPreferencesKey("grid_rows")
        val ICON_SIZE = intPreferencesKey("icon_size")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val LABEL_SIZE = intPreferencesKey("label_size")
        val ENABLE_ANIM = booleanPreferencesKey("enable_anim")
        val DOUBLE_TAP_SLEEP = booleanPreferencesKey("double_tap_sleep")
        val SWIPE_DOWN = stringPreferencesKey("swipe_down")
        val DOCK_APPS = stringPreferencesKey("dock_apps")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val HOME_PAGES = intPreferencesKey("home_pages")
        val CLOCK_24H = booleanPreferencesKey("clock_24h")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_LAT = stringPreferencesKey("weather_lat")
        val WEATHER_LON = stringPreferencesKey("weather_lon")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val CUSTOM_ICONS = stringPreferencesKey("custom_icons")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
    }

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        val customIconsJson = prefs[Keys.CUSTOM_ICONS] ?: "{}"
        val customIcons: Map<String, String> = try {
            gson.fromJson(customIconsJson, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        LauncherSettings(
            themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            gridColumns = prefs[Keys.GRID_COLUMNS] ?: 4,
            gridRows = prefs[Keys.GRID_ROWS] ?: 5,
            iconSizeDp = prefs[Keys.ICON_SIZE] ?: 56,
            showLabels = prefs[Keys.SHOW_LABELS] ?: true,
            labelSizeSp = prefs[Keys.LABEL_SIZE] ?: 12,
            enableAnimations = prefs[Keys.ENABLE_ANIM] ?: true,
            doubleTapToSleep = prefs[Keys.DOUBLE_TAP_SLEEP] ?: false,
            swipeDownAction = try {
                SwipeDownAction.valueOf(prefs[Keys.SWIPE_DOWN] ?: SwipeDownAction.SEARCH.name)
            } catch (_: Exception) { SwipeDownAction.SEARCH },
            dockApps = (prefs[Keys.DOCK_APPS] ?: "").split(",").filter { it.isNotBlank() },
            hiddenApps = prefs[Keys.HIDDEN_APPS] ?: emptySet(),
            homePages = prefs[Keys.HOME_PAGES] ?: 1,
            clockFormat24h = prefs[Keys.CLOCK_24H] ?: true,
            showWeather = prefs[Keys.SHOW_WEATHER] ?: true,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "",
            weatherLat = prefs[Keys.WEATHER_LAT]?.toDoubleOrNull(),
            weatherLon = prefs[Keys.WEATHER_LON]?.toDoubleOrNull(),
            iconPackPackage = prefs[Keys.ICON_PACK],
            customIcons = customIcons
        )
    }

    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val current = settingsFlow.first()
        val updated = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = updated.useDynamicColor
            prefs[Keys.GRID_COLUMNS] = updated.gridColumns
            prefs[Keys.GRID_ROWS] = updated.gridRows
            prefs[Keys.ICON_SIZE] = updated.iconSizeDp
            prefs[Keys.SHOW_LABELS] = updated.showLabels
            prefs[Keys.LABEL_SIZE] = updated.labelSizeSp
            prefs[Keys.ENABLE_ANIM] = updated.enableAnimations
            prefs[Keys.DOUBLE_TAP_SLEEP] = updated.doubleTapToSleep
            prefs[Keys.SWIPE_DOWN] = updated.swipeDownAction.name
            prefs[Keys.DOCK_APPS] = updated.dockApps.joinToString(",")
            prefs[Keys.HIDDEN_APPS] = updated.hiddenApps
            prefs[Keys.HOME_PAGES] = updated.homePages
            prefs[Keys.CLOCK_24H] = updated.clockFormat24h
            prefs[Keys.SHOW_WEATHER] = updated.showWeather
            prefs[Keys.WEATHER_CITY] = updated.weatherCity
            if (updated.weatherLat != null) prefs[Keys.WEATHER_LAT] = updated.weatherLat.toString()
            else prefs.remove(Keys.WEATHER_LAT)
            if (updated.weatherLon != null) prefs[Keys.WEATHER_LON] = updated.weatherLon.toString()
            else prefs.remove(Keys.WEATHER_LON)
            if (updated.iconPackPackage != null) prefs[Keys.ICON_PACK] = updated.iconPackPackage
            else prefs.remove(Keys.ICON_PACK)
            prefs[Keys.CUSTOM_ICONS] = gson.toJson(updated.customIcons)
        }
    }

    suspend fun getInstalledApps(includeHidden: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val hidden = if (includeHidden) emptySet() else settingsFlow.first().hiddenApps

        resolveInfos
            .filter { it.activityInfo.packageName !in hidden }
            .mapNotNull { ri ->
                try {
                    val ai = ri.activityInfo
                    val label = ri.loadLabel(pm)?.toString() ?: ai.packageName
                    val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                    val isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val installTime = try {
                        pm.getPackageInfo(ai.packageName, 0).firstInstallTime
                    } catch (_: Exception) { 0L }
                    AppInfo(
                        packageName = ai.packageName,
                        activityName = ai.name,
                        label = label,
                        icon = icon,
                        isSystemApp = isSystem,
                        installTime = installTime
                    )
                } catch (_: Exception) { null }
            }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.componentKey }
    }

    suspend fun getAllAppsIncludingHidden(): List<AppInfo> = getInstalledApps(includeHidden = true)

    fun launchApp(packageName: String, activityName: String) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(packageName, activityName)
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val launch = pm.getLaunchIntentForPackage(packageName)
            launch?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        }
    }

    suspend fun hideApp(packageName: String) {
        updateSettings { it.copy(hiddenApps = it.hiddenApps + packageName) }
    }

    suspend fun unhideApp(packageName: String) {
        updateSettings { it.copy(hiddenApps = it.hiddenApps - packageName) }
    }

    private fun toDto(item: HomeItem): HomeItemDto = when (item) {
        is HomeItem.AppShortcut -> HomeItemDto(
            type = "app", id = item.id,
            packageName = item.packageName, activityName = item.activityName,
            customIconUri = item.customIconUri,
            page = item.page, cellX = item.cellX, cellY = item.cellY,
            spanX = item.spanX, spanY = item.spanY
        )
        is HomeItem.Folder -> HomeItemDto(
            type = "folder", id = item.id, title = item.title,
            customIconUri = item.customIconUri,
            children = item.children.map { toDto(it) },
            page = item.page, cellX = item.cellX, cellY = item.cellY
        )
        is HomeItem.Widget -> HomeItemDto(
            type = "widget", id = item.id,
            appWidgetId = item.appWidgetId,
            providerPackage = item.providerPackage,
            providerClass = item.providerClass,
            page = item.page, cellX = item.cellX, cellY = item.cellY,
            spanX = item.spanX, spanY = item.spanY
        )
    }

    private fun fromDto(dto: HomeItemDto): HomeItem? = when (dto.type) {
        "app" -> if (dto.packageName != null && dto.activityName != null) {
            HomeItem.AppShortcut(
                id = dto.id, packageName = dto.packageName, activityName = dto.activityName,
                customIconUri = dto.customIconUri,
                page = dto.page, cellX = dto.cellX, cellY = dto.cellY,
                spanX = dto.spanX, spanY = dto.spanY
            )
        } else null
        "folder" -> HomeItem.Folder(
            id = dto.id, title = dto.title ?: "Folder",
            customIconUri = dto.customIconUri,
            children = (dto.children ?: emptyList()).mapNotNull { fromDto(it) as? HomeItem.AppShortcut }.toMutableList(),
            page = dto.page, cellX = dto.cellX, cellY = dto.cellY
        )
        "widget" -> if (dto.appWidgetId != null && dto.providerPackage != null && dto.providerClass != null) {
            HomeItem.Widget(
                id = dto.id, appWidgetId = dto.appWidgetId,
                providerPackage = dto.providerPackage, providerClass = dto.providerClass,
                page = dto.page, cellX = dto.cellX, cellY = dto.cellY,
                spanX = dto.spanX, spanY = dto.spanY
            )
        } else null
        else -> null
    }

    suspend fun saveHomeLayout(items: List<HomeItem>) {
        val dtos = items.map { toDto(it) }
        context.dataStore.edit { prefs ->
            prefs[Keys.HOME_LAYOUT] = gson.toJson(dtos)
        }
    }

    suspend fun loadHomeLayout(): List<HomeItem> {
        val json = context.dataStore.data.first()[Keys.HOME_LAYOUT] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<HomeItemDto>>() {}.type
            val dtos: List<HomeItemDto> = gson.fromJson(json, type)
            dtos.mapNotNull { fromDto(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun exportBackup(): String {
        val settings = settingsFlow.first()
        val homeItems = loadHomeLayout()
        val backup = BackupData(version = 2, settings = settings, homeItems = homeItems.map { toDto(it) })
        return gson.toJson(backup)
    }

    
    suspend fun importBackup(json: String): Boolean {
        return try {
            val backup = gson.fromJson(json, BackupData::class.java)
            if (backup.version > 2) return false
            updateSettings { backup.settings }
            val items = backup.homeItems.mapNotNull { fromDto(it) }
            saveHomeLayout(items)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getBackupFile(): java.io.File {
        return java.io.File(context.filesDir, "lumora_backup.json")
    }
}
