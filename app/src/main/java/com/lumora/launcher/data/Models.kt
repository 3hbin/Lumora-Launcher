package com.lumora.launcher.data

import android.graphics.drawable.Drawable

/**
 * Represents a launchable application installed on the device.
 */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false,
    val installTime: Long = 0L
) {
    val componentKey: String
        get() = "$packageName/$activityName"
}

/**
 * Item placed on the home screen (app shortcut, folder or widget).
 */
sealed class HomeItem {
    abstract val id: String
    abstract var page: Int
    abstract var cellX: Int
    abstract var cellY: Int
    abstract var spanX: Int
    abstract var spanY: Int

    data class AppShortcut(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val customLabel: String? = null,
        val customIconUri: String? = null,
        override var page: Int = 0,
        override var cellX: Int = 0,
        override var cellY: Int = 0,
        override var spanX: Int = 1,
        override var spanY: Int = 1
    ) : HomeItem()

    data class Folder(
        override val id: String,
        var title: String,
        val children: MutableList<AppShortcut> = mutableListOf(),
        var customIconUri: String? = null,
        override var page: Int = 0,
        override var cellX: Int = 0,
        override var cellY: Int = 0,
        override var spanX: Int = 1,
        override var spanY: Int = 1
    ) : HomeItem()

    data class Widget(
        override val id: String,
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String,
        override var page: Int = 0,
        override var cellX: Int = 0,
        override var cellY: Int = 0,
        override var spanX: Int = 2,
        override var spanY: Int = 2
    ) : HomeItem()
}

/**
 * Persistent launcher settings.
 */
data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val gridColumns: Int = 4,
    val gridRows: Int = 5,
    val iconSizeDp: Int = 56,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 12,
    val enableAnimations: Boolean = true,
    val doubleTapToSleep: Boolean = false,
    val swipeDownAction: SwipeDownAction = SwipeDownAction.SEARCH,
    val dockApps: List<String> = emptyList(),
    val hiddenApps: Set<String> = emptySet(),
    val homePages: Int = 1,
    val clockFormat24h: Boolean = true,
    val showWeather: Boolean = true,
    val weatherCity: String = "",
    val weatherLat: Double? = null,
    val weatherLon: Double? = null,
    val iconPackPackage: String? = null,
    val customIcons: Map<String, String> = emptyMap()
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class SwipeDownAction {
    SEARCH, NOTIFICATIONS, NONE
}

/**
 * Weather data from Open-Meteo.
 */
data class WeatherInfo(
    val temperature: Double,
    val weatherCode: Int,
    val description: String,
    val iconName: String,
    val cityName: String,
    val updatedAt: Long,
    val isFromCache: Boolean = false
)

/**
 * Icon pack metadata.
 */
data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Backup payload (JSON serializable).
 */
data class BackupData(
    val version: Int = 2,
    val settings: LauncherSettings,
    val homeItems: List<HomeItemDto>,
    val timestamp: Long = System.currentTimeMillis()
)

data class HomeItemDto(
    val type: String,
    val id: String,
    val packageName: String? = null,
    val activityName: String? = null,
    val title: String? = null,
    val customIconUri: String? = null,
    val children: List<HomeItemDto>? = null,
    val appWidgetId: Int? = null,
    val providerPackage: String? = null,
    val providerClass: String? = null,
    val page: Int = 0,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1
)
