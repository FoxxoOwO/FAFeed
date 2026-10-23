package cz.foxxo.fafeed.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import cz.foxxo.fafeed.data.FA_USER_AGENT
import cz.foxxo.fafeed.data.FaClient
import cz.foxxo.fafeed.data.FaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

fun avatarUrl(author: String): String = "https://a.furaffinity.net/$author.gif"

fun fileNameFor(url: String, fallback: String): String =
    url.substringAfterLast('/').substringBefore('?').ifBlank { fallback }

/**
 * Saves an image either into the folder picked by the user (SAF tree) or, when none is set,
 * through DownloadManager into Downloads/FAFeed.
 *
 * @return true when the file was written or the system download was enqueued.
 */
suspend fun saveImage(
    context: Context,
    client: FaClient,
    url: String,
    cookies: String,
    treeUri: String,
    fallbackName: String,
): Boolean {
    if (url.isBlank()) return false
    val fileName = fileNameFor(url, fallbackName)

    if (treeUri.isBlank()) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "FAFeed/$fileName")
            .addRequestHeader("User-Agent", FA_USER_AGENT)
            .addRequestHeader("Referer", FaParser.BASE + "/")
        if (cookies.isNotBlank()) request.addRequestHeader("Cookie", cookies)
        return runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            true
        }.getOrDefault(false)
    }

    return withContext(Dispatchers.IO) {
        runCatching {
            val bytes = client.getBytes(url)
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                ?: return@runCatching false
            val baseName = fileName.substringBeforeLast('.', fileName)
            val mime = when (fileName.substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            tree.findFile(fileName)?.delete()
            val file = tree.createFile(mime, baseName) ?: return@runCatching false
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
                ?: return@runCatching false
            true
        }.getOrDefault(false)
    }
}
