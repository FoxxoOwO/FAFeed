package cz.foxxo.fafeed.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

const val FA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Safari/537.36"

class FaClient(private val cookiesProvider: () -> String) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun feed(pageUrl: String = FaParser.BASE + "/"): FeedPage = withContext(Dispatchers.IO) {
        FaParser.parseFeed(getHtml(pageUrl), pageUrl)
    }

    suspend fun submission(id: Long): SubmissionDetail = withContext(Dispatchers.IO) {
        val url = "${FaParser.BASE}/view/$id/"
        FaParser.parseSubmission(getHtml(url), url)
    }

    /** Returns the logged-in display name, or null when the cookies are not valid. */
    suspend fun verify(cookies: String): String? = withContext(Dispatchers.IO) {
        runCatching { FaParser.loggedInUser(getHtml(FaParser.BASE + "/", cookies)) }.getOrNull()
    }

    suspend fun getHtml(url: String, cookieOverride: String? = null): String = withContext(Dispatchers.IO) {
        http.newCall(request(url, cookieOverride)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} – ${url}")
            }
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(request(url, null)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} – $url")
            }
            response.body?.bytes() ?: throw IOException("Empty response")
        }
    }

    private fun request(url: String, cookieOverride: String?): Request {
        val cookies = cookieOverride ?: cookiesProvider()
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", FA_USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", FaParser.BASE + "/")
        if (cookies.isNotBlank()) builder.header("Cookie", cookies)
        return builder.build()
    }
}
