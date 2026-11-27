package com.lumora.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher

/**
 * Real AppWidgetHost for Lumora.
 * Host ID is arbitrary but must be unique per app instance.
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

/**
 * Custom HostView so we can later style or intercept long-press for move/resize.
 */
class LumoraAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    override fun getDefaultPadding(): android.graphics.Rect {
        // Minimal padding so widgets look natural on the home grid
        return android.graphics.Rect(0, 0, 0, 0)
    }
}

/**
 * Helper that owns the host lifecycle and provides bind / pick flows.
 */
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

    /**
     * Allocate a new appWidgetId.
     */
    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) { }
    }

    /**
     * List all installed widget providers.
     */
    fun getInstalledProviders(): List<AppWidgetProviderInfo> {
        return appWidgetManager.installedProviders
    }

    /**
     * Bind a widget provider to an already allocated id.
     * Returns true if binding succeeded (or was already allowed).
     * On Android 8+ some providers require user consent via bindAppWidgetIdIfAllowed.
     */
    fun bindWidget(appWidgetId: Int, provider: android.content.ComponentName): Boolean {
        return try {
            if (appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)) {
                true
            } else {
                // Caller should launch ACTION_APPWIDGET_BIND
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Create a host view for an already bound widget.
     */
    fun createView(appWidgetId: Int, providerInfo: AppWidgetProviderInfo?): AppWidgetHostView {
        return host.createView(context, appWidgetId, providerInfo)
    }

    /**
     * Build the intent that asks the user to allow binding when bindAppWidgetIdIfAllowed returns false.
     */
    fun createBindIntent(appWidgetId: Int, provider: android.content.ComponentName): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
    }

    /**
     * Build the configure intent if the provider requires configuration.
     */
    fun createConfigureIntent(appWidgetId: Int, providerInfo: AppWidgetProviderInfo): Intent? {
        if (providerInfo.configure == null) return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = providerInfo.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }
}
