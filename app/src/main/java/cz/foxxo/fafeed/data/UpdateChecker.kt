package cz.foxxo.fafeed.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Dot-separated numeric version, e.g. "v1.1.2" -> [1, 1, 2]. Missing parts count as 0. */
data class Version(val numbers: List<Int>) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val a = numbers.getOrElse(i) { 0 }
            val b = other.numbers.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    override fun toString(): String = numbers.joinToString(".")

    companion object {
        fun parse(raw: String): Version? {
            val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            if (clean.isBlank()) return null
            val numbers = clean.split('.').map { it.toIntOrNull() ?: return null }
            return Version(numbers)
        }
    }
}

data class ReleaseInfo(
    val tag: String,
    val version: Version,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String,
    val pageUrl: String,
)

/** Reads the latest GitHub release; no FA cookies are ever sent here. */
object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/FoxxoOwO/FAFeed/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FAFeed-Updater")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            parseRelease(response.body?.string().orEmpty())
        }
    }

    fun isNewer(release: ReleaseInfo, currentVersion: String): Boolean {
        val current = Version.parse(currentVersion) ?: return false
        return release.version > current
    }

    /** Public for unit tests. Returns null when there is no usable APK asset. */
    fun parseRelease(json: String): ReleaseInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tag = root.optString("tag_name").ifBlank { return null }
        val version = Version.parse(tag) ?: return null
        val assets = root.optJSONArray("assets") ?: return null
        var apkUrl = ""
        var size = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            apkUrl = asset.optString("browser_download_url")
            size = asset.optLong("size")
            break
        }
        if (apkUrl.isBlank()) return null
        return ReleaseInfo(
            tag = tag,
            version = version,
            apkUrl = apkUrl,
            sizeBytes = size,
            notes = root.optString("body"),
            pageUrl = root.optString("html_url"),
        )
    }
}
