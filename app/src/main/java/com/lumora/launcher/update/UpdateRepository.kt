package com.lumora.launcher.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.regex.Pattern

class UpdateRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(UpdateConfig.PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private fun createInitialState(): UpdateUiState {
        val (name, code) = currentVersion()
        return UpdateUiState(
            currentVersionName = name,
            currentVersionCode = code,
            autoCheckDaily = prefs.getBoolean(UpdateConfig.KEY_AUTO_CHECK, true)
        )
    }

    fun currentVersion(): Pair<String, Int> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val name = pInfo.versionName ?: "1.0.0"
            val code = if (Build.VERSION.SDK_INT >= 28) pInfo.longVersionCode.toInt() else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            name to code
        } catch (_: Exception) {
            "1.0.0" to 1
        }
    }

    fun setAutoCheckDaily(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateConfig.KEY_AUTO_CHECK, enabled).apply()
        _uiState.value = _uiState.value.copy(autoCheckDaily = enabled)
    }

    fun shouldAutoCheck(): Boolean {
        if (!prefs.getBoolean(UpdateConfig.KEY_AUTO_CHECK, true)) return false
        val last = prefs.getLong(UpdateConfig.KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last >= UpdateConfig.AUTO_CHECK_INTERVAL_MS
    }

    fun markChecked() {
        prefs.edit().putLong(UpdateConfig.KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /**
     * Query GitHub Releases API for the latest release.
     */
    suspend fun checkForUpdate(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(state = UpdateState.CHECKING, errorMessage = null)
        try {
            val json = httpGet(UpdateConfig.latestReleaseApiUrl)
            val root = gson.fromJson(json, JsonObject::class.java)

            if (root.has("message") && root.get("message").asString.contains("Not Found", ignoreCase = true)) {
                // No releases yet – treat as up to date
                markChecked()
                _uiState.value = _uiState.value.copy(state = UpdateState.UP_TO_DATE, latest = null)
                return@withContext Result.success(null)
            }

            val tagName = root.get("tag_name")?.asString ?: ""
            val name = root.get("name")?.asString ?: tagName
            val body = root.get("body")?.asString ?: ""
            val publishedAt = root.get("published_at")?.asString ?: ""
            val htmlUrl = root.get("html_url")?.asString ?: UpdateConfig.releasesPageUrl

            // Find APK asset
            var apkUrl: String? = null
            var apkSize = 0L
            var sha256: String? = null
            if (root.has("assets")) {
                val assets = root.getAsJsonArray("assets")
                for (el in assets) {
                    val asset = el.asJsonObject
                    val assetName = asset.get("name")?.asString ?: continue
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.get("browser_download_url")?.asString
                        apkSize = asset.get("size")?.asLong ?: 0L
                    }
                    // Optional: look for .sha256 file or checksum in body
                    if (assetName.endsWith(".sha256", ignoreCase = true) ||
                        assetName.contains("checksum", ignoreCase = true)
                    ) {
                        // We only store the URL; actual download of checksum can be added later
                    }
                }
            }

            // Parse versionCode / versionName from tag or body
            // Preferred: tag like "v1.0.1" or "1.0.1" and optional "versionCode: 3" in body
            val versionName = tagName.removePrefix("v").trim().ifBlank { name }
            var versionCode = parseVersionCodeFromBody(body) ?: parseVersionCodeFromTag(versionName)
                ?: (_uiState.value.currentVersionCode + 1)

            // SHA-256 from body lines like "SHA-256: abcdef..." or "sha256=..."
            sha256 = parseSha256FromBody(body)

            val info = ReleaseInfo(
                tagName = tagName,
                name = name,
                body = body,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                versionCode = versionCode,
                versionName = versionName,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = apkSize,
                sha256 = sha256
            )

            markChecked()
            val currentCode = _uiState.value.currentVersionCode
            if (info.versionCode > currentCode && info.apkDownloadUrl != null) {
                _uiState.value = _uiState.value.copy(
                    state = UpdateState.UPDATE_AVAILABLE,
                    latest = info
                )
                Result.success(info)
            } else {
                _uiState.value = _uiState.value.copy(
                    state = UpdateState.UP_TO_DATE,
                    latest = info
                )
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("LumoraUpdate", "Check failed", e)
            _uiState.value = _uiState.value.copy(
                state = UpdateState.ERROR,
                errorMessage = e.message ?: "Check failed"
            )
            Result.failure(e)
        }
    }

    private fun parseVersionCodeFromBody(body: String): Int? {
        val patterns = listOf(
            Pattern.compile("versionCode\\s*[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("version.?code\\s*[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(body)
            if (m.find()) return m.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun parseVersionCodeFromTag(tag: String): Int? {
        // Simple: 1.0.2 → treat as not reliable for versionCode; return null
        // Prefer explicit versionCode in release body
        return null
    }

    private fun parseSha256FromBody(body: String): String? {
        val p = Pattern.compile(
            "(?:SHA-?256|sha256)\\s*[:=]\\s*([A-Fa-f0-9]{64})"
        )
        val m = p.matcher(body)
        return if (m.find()) m.group(1) else null
    }

    /**
     * Download APK to cache dir with progress updates.
     */
    suspend fun downloadApk(info: ReleaseInfo): Result<File> = withContext(Dispatchers.IO) {
        val url = info.apkDownloadUrl ?: return@withContext Result.failure(Exception("No APK URL"))
        _uiState.value = _uiState.value.copy(
            state = UpdateState.DOWNLOADING,
            downloadProgress = 0f,
            downloadedBytes = 0L,
            errorMessage = null
        )
        try {
            val dest = File(context.cacheDir, "lumora-update.apk")
            if (dest.exists()) dest.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "LumoraLauncher-Updater")
                instanceFollowRedirects = true
            }
            val total = conn.contentLengthLong.let { if (it > 0) it else info.apkSizeBytes }
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress,
                            downloadedBytes = downloaded
                        )
                    }
                }
            }

            // Optional SHA-256 check
            if (!info.sha256.isNullOrBlank()) {
                val actual = sha256Of(dest)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    dest.delete()
                    _uiState.value = _uiState.value.copy(
                        state = UpdateState.ERROR,
                        errorMessage = "SHA-256 mismatch – file corrupted or tampered"
                    )
                    return@withContext Result.failure(Exception("SHA-256 mismatch"))
                }
            }

            // Basic package name check via PackageManager (does not install)
            if (!verifyPackageName(dest)) {
                dest.delete()
                _uiState.value = _uiState.value.copy(
                    state = UpdateState.ERROR,
                    errorMessage = "APK package name does not match com.lumora.launcher"
                )
                return@withContext Result.failure(Exception("Package name mismatch"))
            }

            _uiState.value = _uiState.value.copy(
                state = UpdateState.DOWNLOADED,
                downloadProgress = 1f
            )
            Result.success(dest)
        } catch (e: Exception) {
            Log.e("LumoraUpdate", "Download failed", e)
            _uiState.value = _uiState.value.copy(
                state = UpdateState.ERROR,
                errorMessage = e.message ?: "Download failed"
            )
            Result.failure(e)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyPackageName(apk: File): Boolean {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            }
            pi?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LumoraLauncher-Updater")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            throw Exception(err)
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    fun resetState() {
        val (name, code) = currentVersion()
        _uiState.value = UpdateUiState(
            currentVersionName = name,
            currentVersionCode = code,
            autoCheckDaily = prefs.getBoolean(UpdateConfig.KEY_AUTO_CHECK, true)
        )
    }
}
