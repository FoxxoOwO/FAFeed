package cz.foxxo.fafeed.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.foxxo.fafeed.data.FaAuthException
import cz.foxxo.fafeed.data.FaClient
import cz.foxxo.fafeed.data.FaParser
import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.data.Prefs
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class FeedState(
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    val user: String = "",
    val safeOnly: Boolean = false,
    val blurNsfw: Boolean = true,
) {
    val visibleItems: List<FeedItem>
        get() = if (safeOnly) items.filter { !it.isNsfw } else items
}

/** "New submissions" queue = posts from watched users only (the front page mixes in the whole site). */
const val FEED_URL = "https://www.furaffinity.net/msg/submissions/"

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val client = FaClient { prefs.cookies }

    var state by mutableStateOf(
        FeedState(
            user = prefs.user,
            safeOnly = prefs.safeOnly,
            blurNsfw = prefs.blurNsfw,
        )
    )
        private set

    /** Set when FA answers with the login wall – the UI shows the login screen again. */
    var sessionExpired by mutableStateOf(false)
        private set

    private var nextUrl: String? = null
    private var started = false

    init {
        restoreCache()
    }

    fun start() {
        if (started) return
        started = true
        if (state.items.isEmpty()) refresh(initial = true) else refresh()
    }

    fun refresh(initial: Boolean = false) {
        if (state.loading || state.refreshing) return
        state = state.copy(
            loading = initial && state.items.isEmpty(),
            refreshing = !initial || state.items.isNotEmpty(),
            error = null,
        )
        viewModelScope.launch {
            try {
                val page = client.feed(FEED_URL)
                if (page.loginRequired) {
                    sessionExpired = true
                    state = state.copy(loading = false, refreshing = false)
                    return@launch
                }
                nextUrl = page.nextUrl
                val user = page.loggedInUser ?: state.user
                if (user.isNotBlank()) prefs.user = user
                state = state.copy(
                    items = page.items,
                    loading = false,
                    refreshing = false,
                    endReached = false,
                    error = if (page.items.isEmpty()) {
                        "Zatím žádné posty – sleduješ někoho? Feed se naplní, až tví sledovaní něco přidají."
                    } else {
                        null
                    },
                    user = user,
                )
                saveCache(page.items)
            } catch (t: Throwable) {
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = humanError(t),
                )
            }
        }
    }

    fun loadMore() {
        if (state.loading || state.refreshing || state.loadingMore || state.endReached) return
        val url = nextUrl ?: run {
            state = state.copy(endReached = true)
            return
        }
        state = state.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val page = client.feed(url)
                if (page.loginRequired) {
                    sessionExpired = true
                    state = state.copy(loadingMore = false)
                    return@launch
                }
                val known = state.items.mapTo(HashSet()) { it.id }
                val fresh = page.items.filter { it.id !in known }
                nextUrl = page.nextUrl
                state = state.copy(
                    items = state.items + fresh,
                    loadingMore = false,
                    endReached = (page.items.isEmpty() || page.nextUrl == null || fresh.isEmpty()),
                )
            } catch (t: Throwable) {
                state = state.copy(loadingMore = false, error = humanError(t))
            }
        }
    }

    fun setSafeOnly(value: Boolean) {
        prefs.safeOnly = value
        state = state.copy(safeOnly = value)
    }

    fun setBlurNsfw(value: Boolean) {
        prefs.blurNsfw = value
        state = state.copy(blurNsfw = value)
    }

    fun logout() {
        prefs.clearSession()
        state = FeedState()
        nextUrl = null
        started = false
        sessionExpired = true
    }

    fun acknowledgeExpired() {
        sessionExpired = false
    }

    fun onLoggedIn(cookies: String, user: String) {
        prefs.cookies = cookies
        prefs.user = user
        sessionExpired = false
        state = FeedState(user = user, safeOnly = prefs.safeOnly, blurNsfw = prefs.blurNsfw)
        nextUrl = null
        started = false
        start()
    }

    private fun humanError(t: Throwable): String = when (t) {
        is FaAuthException -> "Přihlášení na FurAffinity vypršelo. Přihlas se znovu."
        else -> "Načtení selhalo: ${t.message ?: t::class.simpleName}"
    }

    private fun saveCache(items: List<FeedItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("author", item.author)
                    put("authorDisplay", item.authorDisplay)
                    put("rating", item.rating)
                    put("type", item.type)
                    put("thumbUrl", item.thumbUrl)
                    put("width", item.width.toDouble())
                    put("height", item.height.toDouble())
                }
            )
        }
        prefs.cachedFeed = array.toString()
    }

    private fun restoreCache() {
        val raw = prefs.cachedFeed
        if (raw.isBlank() || state.items.isNotEmpty()) return
        runCatching {
            val array = JSONArray(raw)
            val items = (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                FeedItem(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    author = o.optString("author"),
                    authorDisplay = o.optString("authorDisplay"),
                    rating = o.optString("rating", "general"),
                    type = o.optString("type", "image"),
                    thumbUrl = o.optString("thumbUrl"),
                    width = o.optDouble("width", 0.0).toFloat(),
                    height = o.optDouble("height", 0.0).toFloat(),
                )
            }.filter { it.id > 0 }
            if (items.isNotEmpty()) {
                state = state.copy(items = items)
            }
        }
    }
}
