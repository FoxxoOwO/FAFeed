package cz.foxxo.fafeed.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fallback UA for installs that never signed in through the WebView.
 *
 * Once a login goes through, the WebView's own UA is stored in [Prefs.userAgent] and sent
 * instead – Cloudflare binds `cf_clearance` to the UA, so both clients must match.
 */
const val FA_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Safari/537.36"

class FaClient(
    private val userAgentProvider: () -> String = { FA_USER_AGENT },
    private val cookiesProvider: () -> String,
) {

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

    /**
     * Returns the logged-in display name, or null when the page carries no login state.
     *
     * @throws FaBlockedException when Cloudflare intercepts the request instead of FA answering.
     */
    suspend fun verify(cookies: String): String? = withContext(Dispatchers.IO) {
        FaParser.loggedInUser(getHtml(FaParser.BASE + "/", cookies))
    }

    suspend fun getHtml(url: String, cookieOverride: String? = null): String = withContext(Dispatchers.IO) {
        http.newCall(request(url, cookieOverride)).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (isChallenge(response, body)) throw FaBlockedException("Cloudflare challenge – $url")
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} – $url")
            }
            if (body.isBlank()) throw IOException("Empty response")
            body
        }
    }

    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(request(url, null)).execute().use { response ->
            if (isMitigated(response)) throw FaBlockedException("Cloudflare challenge – $url")
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} – $url")
            }
            response.body?.bytes() ?: throw IOException("Empty response")
        }
    }

    /** Cloudflare tags challenged responses with `cf-mitigated: challenge`. */
    private fun isMitigated(response: Response): Boolean =
        response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true

    private fun isChallenge(response: Response, body: String): Boolean {
        if (isMitigated(response)) return true
        if (response.code != 403 && response.code != 503) return false
        val server = response.header("Server").orEmpty()
        val cloudflare = server.contains("cloudflare", ignoreCase = true)
        val looksLikeInterstitial = body.contains("Just a moment", ignoreCase = true) ||
            body.contains("cf-chl", ignoreCase = true) ||
            body.contains("_cf_chl_opt", ignoreCase = true) ||
            body.contains("challenge-platform", ignoreCase = true)
        return cloudflare && looksLikeInterstitial
    }

    private fun request(url: String, cookieOverride: String?): Request {
        val cookies = cookieOverride ?: cookiesProvider()
        val agent = userAgentProvider().ifBlank { FA_USER_AGENT }
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", agent)
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
