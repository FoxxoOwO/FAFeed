package cz.foxxo.fafeed.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * HTML parser for the FurAffinity "beta" (current) layout.
 *
 * Feed pages:  https://www.furaffinity.net/            (submissions from watched users)
 *              https://www.furaffinity.net/msg/submissions/  (fallback / paging)
 * Card markup:  <figure id="sid-123" class="r-general t-image u-name"> ... </figure>
 * Submission:   https://www.furaffinity.net/view/123/
 */
object FaParser {

    const val BASE = "https://www.furaffinity.net"

    private val THUMB_SIZE = Regex("@\\d{3,4}(?=[-.])")

    fun parseFeed(html: String, pageUrl: String): FeedPage {
        val doc = Jsoup.parse(html, pageUrl)
        val loginRequired = doc.title().contains("Login Required", ignoreCase = true)
        val user = doc.selectFirst("img.loggedin_user_avatar")
            ?.attr("alt")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val figures = doc.select("figure[id^=sid-]")
        val items = figures.mapNotNull { parseFigure(it) }.distinctBy { it.id }

        return FeedPage(
            items = items,
            nextUrl = if (loginRequired) null else findNextUrl(doc, pageUrl) ?: findLegacyNext(doc, pageUrl),
            loggedInUser = user,
            loginRequired = loginRequired,
        )
    }

    fun parseFigure(fig: Element): FeedItem? {
        val id = fig.id().removePrefix("sid-").toLongOrNull() ?: return null
        val titleEl = fig.selectFirst("figcaption a[href^=/view/]") ?: fig.selectFirst("a[href^=/view/]")
        val authorEl = fig.selectFirst("figcaption a[href^=/user/]") ?: fig.selectFirst("a[href^=/user/]")
        val img = fig.selectFirst("img") ?: return null

        val rawThumb = img.attr("src").ifBlank { img.attr("data-src") }
        if (rawThumb.isBlank()) return null

        val classes = fig.classNames()
        val rating = classes.firstOrNull { it.startsWith("r-") }?.removePrefix("r-") ?: "general"
        val type = classes.firstOrNull { it.startsWith("t-") }?.removePrefix("t-") ?: "image"

        val title = titleEl?.attr("title")?.takeIf { it.isNotBlank() } ?: titleEl?.text().orEmpty()
        val author = authorEl?.attr("href")
            ?.removePrefix("/user/")
            ?.trim('/')
            .orEmpty()
        val authorDisplay = authorEl?.text()?.takeIf { it.isNotBlank() } ?: author

        return FeedItem(
            id = id,
            title = title.trim(),
            author = author,
            authorDisplay = authorDisplay.trim(),
            rating = rating,
            type = type,
            thumbUrl = upscaleThumb(normalizeUrl(rawThumb)),
            width = img.attr("data-width").toFloatOrNull() ?: 0f,
            height = img.attr("data-height").toFloatOrNull() ?: 0f,
        )
    }

    /** All submission cards on a page (gallery, scraps, msg list, front page...). */
    fun parseFigures(html: String, pageUrl: String): List<FeedItem> =
        Jsoup.parse(html, pageUrl)
            .select("figure[id^=sid-]")
            .mapNotNull { parseFigure(it) }
            .distinctBy { it.id }

    /**
     * Watchlist page -> usernames + next page url.
     * Markup: <div class="watch-list-items ..."><span class="c-usernameBlockSimple"><a href="/user/name/">
     */
    fun parseWatchlist(html: String, pageUrl: String): WatchlistPage {
        val doc = Jsoup.parse(html, pageUrl)
        if (doc.title().contains("Login Required", ignoreCase = true)) throw FaAuthException()

        val users = LinkedHashSet<String>()
        for (block in doc.select("div.watch-list-items")) {
            val href = block.selectFirst("a[href^=/user/]")?.attr("href") ?: continue
            users += href.removePrefix("/user/").trim('/').lowercase()
        }
        if (users.isEmpty()) {
            for (link in doc.select("a[href^=/user/]")) {
                val name = link.attr("href").removePrefix("/user/").trim('/')
                if (name.isNotEmpty() && !name.contains("/")) users += name.lowercase()
            }
        }

        val next = doc.selectFirst("div.mini-nav a[href*=/watchlist/], a[href*=/watchlist/by/][rel=next]")
            ?.attr("href")
            ?.let { resolve(pageUrl, it) }
            ?: doc.select("a[href*=/watchlist/by/]")
                .firstOrNull { it.text().contains("next", ignoreCase = true) }
                ?.attr("href")
                ?.let { resolve(pageUrl, it) }

        return WatchlistPage(users.toList(), next)
    }

    fun parseSubmission(html: String, pageUrl: String): SubmissionDetail {
        val doc = Jsoup.parse(html, pageUrl)
        if (doc.title().contains("Login Required", ignoreCase = true)) {
            throw FaAuthException()
        }

        val id = doc.selectFirst("meta[property=og:url]")
            ?.attr("content")
            ?.trim('/')
            ?.substringAfterLast('/')
            ?.toLongOrNull()
            ?: Regex("/view/(\\d+)").find(pageUrl)?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L

        val title = doc.selectFirst(".submission-title > h2")?.text()?.trim()
            ?: doc.selectFirst(".submission-title")?.text()?.trim()
            ?: ""

        val authorBlock = doc.selectFirst(".submission-description-artist")
        val authorLink = authorBlock?.selectFirst("a[href^=/user/]")
        val authorDisplay = authorLink?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: authorBlock?.text()?.trim().orEmpty()
        val author = authorLink?.attr("href")?.removePrefix("/user/")?.trim('/').orEmpty()

        val image = doc.selectFirst("img#submissionImg")
        val imageUrl = normalizeUrl(
            image?.attr("data-fullview-src")?.takeIf { it.isNotBlank() }
                ?: image?.attr("src").orEmpty()
        )

        val dateEpoch = doc.selectFirst("span.popup_date[data-time]")?.attr("data-time")?.toLongOrNull()

        val descriptionEl = doc.selectFirst(".submission-description-text")
        val description = descriptionEl?.let { plainText(it) }.orEmpty()

        val stats = doc.select(".submission-page-stats > div").mapNotNull { stat ->
            val cells = stat.children().map { it.text().trim() }
            if (cells.size < 2) return@mapNotNull null
            val label = cells[1].lowercase()
            if (label.isBlank()) null else label to cells[0]
        }.toMap()

        val tags = doc.select(".submission-tags a[href^=/search/]")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }

        val rating = stats.entries.firstOrNull { it.key.startsWith("rating") }?.value
            ?: doc.selectFirst("div#submission_page[class^=page-content-type]")
                ?.classNames()
                ?.firstOrNull { it.contains("type", ignoreCase = true) }
                ?.substringAfterLast('-')
                .orEmpty()

        val downloadUrl = doc.select("#submission-options a")
            .firstOrNull { it.text().trim().equals("download", ignoreCase = true) }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let { resolve(pageUrl, it) }

        return SubmissionDetail(
            id = id,
            title = title,
            author = author,
            authorDisplay = authorDisplay.ifBlank { author },
            imageUrl = imageUrl,
            dateEpoch = dateEpoch,
            description = description,
            views = stats["views"].orEmpty(),
            favorites = stats.entries.firstOrNull { it.key.startsWith("fav") }?.value.orEmpty(),
            comments = stats.entries.firstOrNull { it.key.startsWith("comment") }?.value.orEmpty(),
            rating = rating,
            tags = tags,
            downloadUrl = downloadUrl,
        )
    }

    /** Returns the display name from a page that only renders it when logged in. */
    fun loggedInUser(html: String): String? =
        Jsoup.parse(html)
            .selectFirst("img.loggedin_user_avatar")
            ?.attr("alt")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun findNextUrl(doc: Document, pageUrl: String): String? {
        for (a in doc.select("a[href]")) {
            val href = a.attr("href").trim()
            if (href.isEmpty() || href == "#" || href.startsWith("javascript", ignoreCase = true)) continue
            val text = a.text().trim().lowercase()
            val matches = text.startsWith("next") || text.startsWith("older") ||
                text.startsWith(">") || text.startsWith("«older") || text.contains(" next ")
            if (!matches) continue
            val absolute = resolve(pageUrl, href) ?: continue
            if (!absolute.startsWith(BASE)) continue
            if (absolute.contains("/login")) continue
            return absolute
        }

        for (form in doc.select("form")) {
            val pageInput = form.selectFirst("input[name=page]") ?: continue
            val buttonText = form.select("button").joinToString(" ") { it.text() }.lowercase()
            if (!buttonText.contains("next") && !buttonText.contains("older")) continue
            val action = form.attr("action").ifBlank { pageUrl }
            val absolute = resolve(pageUrl, action) ?: continue
            val sep = if (absolute.contains("?")) "&" else "?"
            return absolute + sep + "page=" + pageInput.attr("value")
        }
        return null
    }

    /** Classic layout / msg pages use "Next 48" style links inside #submission-list. */
    private fun findLegacyNext(doc: Document, pageUrl: String): String? =
        doc.select("a[href]")
            .firstOrNull { it.text().contains("next", ignoreCase = true) }
            ?.attr("href")
            ?.let { resolve(pageUrl, it) }

    fun normalizeUrl(raw: String): String {
        val url = raw.trim()
        return when {
            url.isBlank() -> ""
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> BASE + url
            url.startsWith("http") -> url
            else -> "$BASE/$url"
        }
    }

    /** The feed serves 200px thumbnails; the CDN also has 600px variants of the same file. */
    fun upscaleThumb(url: String): String = url.replace(THUMB_SIZE, "@600")

    fun resolve(base: String, href: String): String? = try {
        URI(base).resolve(href.trim()).toString()
    } catch (_: Exception) {
        null
    }

    private fun plainText(el: Element): String {
        val withBreaks = el.html().replace(Regex("(?i)<br\\s*/?>"), "\n")
        val stripped = withBreaks.replace(Regex("(?s)<[^>]*>"), "")
        val text = org.jsoup.parser.Parser.unescapeEntities(stripped, false)
        return text
            .replace("\u00a0", " ")
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
