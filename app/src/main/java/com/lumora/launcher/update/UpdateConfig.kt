package com.lumora.launcher.update

/**
 * Official update source – GitHub Releases for 3hbin/Lumora-Launcher.
 * Change these only if the repository moves.
 */
object UpdateConfig {
    const val GITHUB_OWNER = "3hbin"
    const val GITHUB_REPO = "Lumora-Launcher"

    /** GitHub Releases API – latest release */
    val latestReleaseApiUrl: String
        get() = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** Human-readable releases page */
    val releasesPageUrl: String
        get() = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    const val PREFS_NAME = "lumora_update"
    const val KEY_LAST_CHECK = "last_check_ms"
    const val KEY_AUTO_CHECK = "auto_check_daily"
    const val KEY_IGNORED_VERSION = "ignored_version_code"

    /** Minimum interval between automatic checks (23 hours) */
    const val AUTO_CHECK_INTERVAL_MS = 23 * 60 * 60 * 1000L
}
