package cz.foxxo.fafeed.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException

/** Downloads the release APK into the app's own cache, so no storage permission is needed. */
class UpdateRepository(context: Context) {
    private val cacheDir = File(context.applicationContext.cacheDir, "updates")
    private val client = OkHttpClient.Builder().build()
    private var currentCall: Call? = null

    suspend fun download(release: ReleaseInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        val target = apkFile(release)
        // Drop APKs left over from earlier releases; the cache is not meant to grow.
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".apk") && file.name != target.name) file.delete()
        }

        val call = client.newCall(Request.Builder().url(release.apkUrl).build())
        currentCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                BufferedInputStream(body.byteStream()).use { input ->
                    BufferedOutputStream(target.outputStream()).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            read += count
                            if (total > 0) onProgress((read * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (target.length() == 0L) throw IOException("Empty APK")
            onProgress(100)
            target
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally {
            currentCall = null
        }
    }

    fun cancel() {
        currentCall?.cancel()
    }

    private fun apkFile(release: ReleaseInfo): File {
        val safeVersion = release.tag.filter { it.isDigit() || it == '.' || it == '-' }
            .ifBlank { "update" }
        return File(cacheDir, "fafeed-$safeVersion.apk")
    }
}
