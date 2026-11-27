package com.lumora.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/**
 * Real AppWidgetHost for Lumora.
 */
class LumoraAppWidgetHost(
    context: Context,
    hostId: Int = HOST_ID
) : AppWidgetHost(context, hostId) {

    companion object {
        const val HOST_ID = 0x4C554D // "LUM"
    }

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LumoraAppWidgetHostView(context)
    }
}

class LumoraAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
}

class WidgetManager(private val context: Context) {

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host: LumoraAppWidgetHost = LumoraAppWidgetHost(context)

    fun startListening() {
        try {
            host.startListening()
        } catch (_: Exception) { }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (_: Exception) { }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) { }
    }

    fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        return appWidgetManager.installedProviders
    }

    fun bindWidget(appWidgetId: Int, provider: android.content.ComponentName): Boolean {
        return try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        } catch (_: Exception) {
            false
        }
    }

    fun createView(appWidgetId: Int, providerInfo: AppWidgetProviderInfo?): AppWidgetHostView {
        return host.createView(context, appWidgetId, providerInfo)
    }

    fun createBindIntent(appWidgetId: Int, provider: android.content.ComponentName): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
    }

    fun createConfigureIntent(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Intent? {
        if (providerInfo.configure == null) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = providerInfo.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }
}
