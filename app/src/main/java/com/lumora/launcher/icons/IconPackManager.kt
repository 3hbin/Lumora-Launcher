package com.lumora.launcher.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.lumora.launcher.data.IconPackInfo

/**
 * Detects and loads icon packs that follow common Android launcher conventions
 * (including CandyBar-style packs that declare the activity filter below).
 */
class IconPackManager(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * Find installed icon packs by looking for apps that advertise
     * the standard icon-pack intent filter used by many launchers / CandyBar.
     */
    fun getInstalledIconPacks(): List<IconPackInfo> {
        val intent = Intent("org.adw.launcher.THEMES")
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        // Also try another common filter
        val intent2 = Intent("com.novalauncher.THEME")
        val resolveInfos2 = pm.queryIntentActivities(intent2, PackageManager.MATCH_ALL)

        val packs = mutableMapOf<String, IconPackInfo>()
        (resolveInfos + resolveInfos2).forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in packs) {
                packs[pkg] = IconPackInfo(
                    packageName = pkg,
                    label = ri.loadLabel(pm)?.toString() ?: pkg,
                    icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                )
            }
        }
        return packs.values.sortedBy { it.label.lowercase() }
    }

    /**
     * Attempt to load a themed icon for the given component from an icon pack.
     * Returns null if the pack does not provide an icon for this component.
     *
     * Common approach used by many launchers: look up drawable named after
     * the component (with dots/slashes replaced) inside the pack's resources.
     */
    fun loadIconFromPack(
        iconPackPackage: String,
        targetPackage: String,
        targetActivity: String
    ): Drawable? {
        return try {
            val res = pm.getResourcesForApplication(iconPackPackage)
            // CandyBar / ADW style: component name with _ instead of .
            val candidates = listOf(
                "${targetPackage.replace('.', '_')}_${targetActivity.substringAfterLast('.').replace('.', '_')}",
                targetPackage.replace('.', '_'),
                "${targetPackage}.${targetActivity}".replace('.', '_')
            )
            for (name in candidates) {
                val id = res.getIdentifier(name.lowercase(), "drawable", iconPackPackage)
                if (id != 0) {
                    return res.getDrawable(id, null)
                }
            }
            // Try appfilter.xml style (simplified – full parsing is heavier)
            null
        } catch (_: Exception) {
            null
        }
    }

    fun loadPackIcon(iconPackPackage: String): Drawable? {
        return try {
            pm.getApplicationIcon(iconPackPackage)
        } catch (_: Exception) {
            null
        }
    }
}
