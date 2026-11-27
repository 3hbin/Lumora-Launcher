package com.lumora.launcher.update

/**
 * Parsed information about a GitHub Release.
 */
data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,           // changelog (markdown)
    val publishedAt: String,
    val htmlUrl: String,
    val versionCode: Int,       // parsed from assets or tag
    val versionName: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val sha256: String?         // from release body or asset name if present
)

enum class UpdateState {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    ERROR
}

data class UpdateUiState(
    val state: UpdateState = UpdateState.IDLE,
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0,
    val latest: ReleaseInfo? = null,
    val downloadProgress: Float = 0f,       // 0f..1f
    val downloadedBytes: Long = 0L,
    val errorMessage: String? = null,
    val autoCheckDaily: Boolean = true
)
