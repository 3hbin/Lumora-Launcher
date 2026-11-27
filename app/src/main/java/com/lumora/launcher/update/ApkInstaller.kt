package com.lumora.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Intentionally does NOT install APKs in-app.
 * Play Protect treats REQUEST_INSTALL_PACKAGES + APK download as a high-risk pattern.
 * Users install updates from the official GitHub Releases page in a browser.
 */
object ApkInstaller {

    fun openReleasesPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UpdateConfig.releasesPageUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** @deprecated In-app install disabled for security posture. */
    fun install(context: Context, apkFile: File) {
        openReleasesPage(context)
    }

    fun canRequestInstall(context: Context): Boolean = false

    fun openInstallPermissionSettings(context: Context) {
        openReleasesPage(context)
    }
}
