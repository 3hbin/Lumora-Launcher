package com.lumora.launcher

import android.app.Application
import com.lumora.launcher.data.LauncherRepository
import com.lumora.launcher.icons.IconPackManager
import com.lumora.launcher.update.UpdateCheckWorker
import com.lumora.launcher.update.UpdateRepository
import com.lumora.launcher.weather.WeatherRepository
import com.lumora.launcher.widgets.WidgetManager

class LumoraApp : Application() {
    lateinit var repository: LauncherRepository
        private set
    lateinit var weatherRepository: WeatherRepository
        private set
    lateinit var iconPackManager: IconPackManager
        private set
    lateinit var widgetManager: WidgetManager
        private set
    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = LauncherRepository(this)
        weatherRepository = WeatherRepository(this)
        iconPackManager = IconPackManager(this)
        widgetManager = WidgetManager(this)
        updateRepository = UpdateRepository(this)
        widgetManager.startListening()
        // Schedule daily update check (respects user preference inside worker)
        UpdateCheckWorker.enqueue(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            widgetManager.stopListening()
        } catch (_: Exception) { }
    }
}
