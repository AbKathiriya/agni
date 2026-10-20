package com.akash.agni.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater for a sideloaded personal app. Each GitHub Actions build publishes a release tagged
 * "build-N" with the APK attached; N is also the app's versionCode. If the latest release is newer than
 * what's installed, the app downloads the APK and hands it to Android's installer. Updates install over
 * the existing app (keeping your data) because every build is signed with the same key.
 */
class Updater(private val context: Context) {

    data class Release(val build: Int, val apkUrl: String, val sizeBytes: Long)

    private val latestUrl = "https://api.github.com/repos/AbKathiriya/agni/releases/latest"
    private var lastCheck = 0L
    private var cached: Release? = null

    val currentBuild: Long
        get() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    val currentVersionName: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Returns a newer release, or null if up to date or offline. Cached for 30 minutes unless forced. */
    suspend fun check(force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastCheck < 30 * 60_000) return@withContext cached
        val result = runCatching {
            val conn = URL(latestUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = try {
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val json = JSONObject(body)
            val build = json.getString("tag_name").removePrefix("build-").toIntOrNull() ?: return@runCatching null
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
            Release(build, apk.getString("browser_download_url"), apk.optLong("size"))
        }.getOrNull()
        lastCheck = System.currentTimeMillis()
        cached = result?.takeIf { it.build > currentBuild }
        cached
    }

    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "agni-${release.build}.apk")
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        file
    }

    /** Android requires a one-time "Install unknown apps" permission for Agni. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
