package cz.foxxo.fafeed.ui

import java.net.URI

sealed interface FaDeepLink {
    data class Submission(val id: Long) : FaDeepLink
    data class Profile(val user: String) : FaDeepLink
}

/** Parses https://www.furaffinity.net/view/123/ and /user/name/ links. */
fun parseFaDeepLink(url: String?): FaDeepLink? {
    if (url.isNullOrBlank()) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "https") return null
    val host = uri.host?.lowercase().orEmpty()
    if (host != "furaffinity.net" && host != "www.furaffinity.net") return null

    val segments = uri.path.orEmpty().trim('/').split('/')
    return when (segments.getOrNull(0)) {
        "view" -> segments.getOrNull(1)?.toLongOrNull()?.let { FaDeepLink.Submission(it) }
        "user" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { normalizeUser(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { FaDeepLink.Profile(it) }
        else -> null
    }
}
