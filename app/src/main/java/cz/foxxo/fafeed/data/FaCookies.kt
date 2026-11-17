package cz.foxxo.fafeed.data

/**
 * Helpers for the cookie jar shared by the login WebView and OkHttp.
 *
 * FurAffinity signs users in on `login.furaffinity.net` (Django SSO) and then hands the
 * session over to `www.furaffinity.net`. The two hosts can therefore carry different
 * cookies, and the challenge cookie Cloudflare sets on `www` must travel with every request.
 */
object FaCookies {

    /** FurAffinity's Django SSO host – it holds the real login form and its own cookies. */
    const val LOGIN_HOST = "https://login.furaffinity.net"

    /** Cookie names that carry no login state – a jar with only these is still signed out. */
    private val NON_SESSION = setOf(
        "cf_clearance",
        "cf_chl_2",
        "cf_chl_prog",
        "__cf_bm",
        "_cfuvid",
        "csrftoken",
        "fa-cache-clear",
    )

    /**
     * Merges the `Cookie` headers of several hosts into one header value.
     * Earlier entries win when the same cookie name appears twice, so pass `www` first.
     */
    fun merge(perHost: List<String?>): String {
        val merged = LinkedHashMap<String, String>()
        for (header in perHost) {
            if (header.isNullOrBlank()) continue
            for (part in header.split(';')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val name = part.substring(0, eq).trim()
                if (name.isEmpty()) continue
                merged.putIfAbsent(name, part.substring(eq + 1).trim())
            }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** False when the jar only holds Cloudflare/CSRF crumbs, i.e. the user is not signed in yet. */
    fun hasSession(cookies: String): Boolean {
        if (cookies.isBlank()) return false
        return cookies.split(';').any { part ->
            val name = part.substringBefore('=').trim().lowercase()
            name.isNotEmpty() && name !in NON_SESSION
        }
    }
}
