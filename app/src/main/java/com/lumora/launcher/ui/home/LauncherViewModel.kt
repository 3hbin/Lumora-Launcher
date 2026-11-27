package com.lumora.launcher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumora.launcher.data.*
import com.lumora.launcher.weather.WeatherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class LauncherViewModel(
    private val repository: LauncherRepository,
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    val settings: StateFlow<LauncherSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LauncherSettings())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _homeItems = MutableStateFlow<List<HomeItem>>(emptyList())
    val homeItems: StateFlow<List<HomeItem>> = _homeItems.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading.asStateFlow()

    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolderId: StateFlow<String?> = _openFolderId.asStateFlow()

    init {
        refreshApps()
        loadHomeLayout()
        // Load cached weather first
        viewModelScope.launch {
            _weather.value = weatherRepository.getCached()
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            _apps.value = repository.getInstalledApps()
        }
    }

    fun loadHomeLayout() {
        viewModelScope.launch {
            _homeItems.value = repository.loadHomeLayout()
        }
    }

    fun openDrawer() {
        _isDrawerOpen.value = true
        _searchQuery.value = ""
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun launchApp(app: AppInfo) {
        repository.launchApp(app.packageName, app.activityName)
        closeDrawer()
        closeFolder()
    }

    fun launchApp(packageName: String, activityName: String) {
        repository.launchApp(packageName, activityName)
        closeFolder()
    }

    fun hideApp(packageName: String) {
        viewModelScope.launch {
            repository.hideApp(packageName)
            refreshApps()
        }
    }

    fun unhideApp(packageName: String) {
        viewModelScope.launch {
            repository.unhideApp(packageName)
            refreshApps()
        }
    }

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }

    fun openFolder(id: String) {
        _openFolderId.value = id
    }

    fun closeFolder() {
        _openFolderId.value = null
    }

    fun addAppToHome(app: AppInfo, page: Int = 0, cellX: Int = 0, cellY: Int = 0) {
        viewModelScope.launch {
            val current = _homeItems.value.toMutableList()
            // Avoid exact duplicate on same page
            val exists = current.any {
                it is HomeItem.AppShortcut &&
                        it.packageName == app.packageName &&
                        it.activityName == app.activityName &&
                        it.page == page
            }
            if (exists) return@launch
            val shortcut = HomeItem.AppShortcut(
                id = UUID.randomUUID().toString(),
                packageName = app.packageName,
                activityName = app.activityName,
                page = page,
                cellX = cellX,
                cellY = cellY
            )
            current.add(shortcut)
            _homeItems.value = current
            repository.saveHomeLayout(current)
        }
    }

    fun removeHomeItem(id: String) {
        viewModelScope.launch {
            val current = _homeItems.value.filter { it.id != id }
            _homeItems.value = current
            repository.saveHomeLayout(current)
        }
    }

    fun createFolderFromTwoApps(a: HomeItem.AppShortcut, b: HomeItem.AppShortcut, title: String = "Folder") {
        viewModelScope.launch {
            val folder = HomeItem.Folder(
                id = UUID.randomUUID().toString(),
                title = title,
                children = mutableListOf(a, b),
                page = a.page,
                cellX = a.cellX,
                cellY = a.cellY
            )
            val current = _homeItems.value.toMutableList()
            current.removeAll { it.id == a.id || it.id == b.id }
            current.add(folder)
            _homeItems.value = current
            repository.saveHomeLayout(current)
        }
    }

    fun renameFolder(folderId: String, newTitle: String) {
        viewModelScope.launch {
            val current = _homeItems.value.map { item ->
                if (item is HomeItem.Folder && item.id == folderId) item.copy(title = newTitle) else item
            }
            _homeItems.value = current
            repository.saveHomeLayout(current)
        }
    }

    fun addAppToFolder(folderId: String, app: HomeItem.AppShortcut) {
        viewModelScope.launch {
            val current = _homeItems.value.map { item ->
                if (item is HomeItem.Folder && item.id == folderId) {
                    val children = item.children.toMutableList()
                    if (children.none { it.componentKey() == app.componentKey() }) {
                        children.add(app)
                    }
                    item.copy(children = children)
                } else item
            }.filter { it.id != app.id } // remove from home if it was a free shortcut
            _homeItems.value = current
            repository.saveHomeLayout(current)
        }
    }

    fun removeAppFromFolder(folderId: String, appId: String) {
        viewModelScope.launch {
            val current = _homeItems.value.mapNotNull { item ->
                if (item is HomeItem.Folder && item.id == folderId) {
                    val children = item.children.filter { it.id != appId }.toMutableList()
                    if (children.isEmpty()) null // auto-delete empty folder
                    else item.copy(children = children)
                } else item
            }
            _homeItems.value = current
            repository.saveHomeLayout(current)
        }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = updateSettings { it.copy(useDynamicColor = enabled) }
    fun setGrid(columns: Int, rows: Int) = updateSettings { it.copy(gridColumns = columns, gridRows = rows) }

    fun refreshWeather() {
        viewModelScope.launch {
            val s = settings.value
            _weatherLoading.value = true
            try {
                if (s.weatherLat != null && s.weatherLon != null) {
                    weatherRepository.fetchWeather(s.weatherLat, s.weatherLon, s.weatherCity)
                        .onSuccess { _weather.value = it }
                } else if (s.weatherCity.isNotBlank()) {
                    weatherRepository.searchCity(s.weatherCity)
                        .onSuccess { (lat, lon) ->
                            updateSettings { it.copy(weatherLat = lat, weatherLon = lon) }
                            weatherRepository.fetchWeather(lat, lon, s.weatherCity)
                                .onSuccess { _weather.value = it }
                        }
                } else {
                    // Try cache only
                    _weather.value = weatherRepository.getCached()
                }
            } finally {
                _weatherLoading.value = false
            }
        }
    }

    fun setWeatherCity(city: String) {
        updateSettings { it.copy(weatherCity = city, weatherLat = null, weatherLon = null) }
        // Trigger fetch after city change
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            refreshWeather()
        }
    }

    fun filteredApps(): List<AppInfo> {
        val query = _searchQuery.value.trim().lowercase()
        val list = _apps.value
        return if (query.isEmpty()) list
        else list.filter { it.label.lowercase().contains(query) }
    }

    private fun HomeItem.AppShortcut.componentKey() = "$packageName/$activityName"

    class Factory(
        private val repository: LauncherRepository,
        private val weatherRepository: WeatherRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(repository, weatherRepository) as T
        }
    }
}
