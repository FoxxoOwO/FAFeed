package cz.foxxo.fafeed.ui

import cz.foxxo.fafeed.R
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.foxxo.fafeed.data.FaAuthException
import cz.foxxo.fafeed.data.FaBlockedException
import cz.foxxo.fafeed.data.FaClient
import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.data.FeedRepository
import cz.foxxo.fafeed.data.FeedStore
import cz.foxxo.fafeed.data.Prefs
import cz.foxxo.fafeed.data.SyncMode
import kotlinx.coroutines.launch

/** Accepts "@Name", "name/" or a profile URL ending and stores a canonical lowercase name. */
fun normalizeUser(raw: String): String = raw.trim()
    .removePrefix("@")
    .substringBefore('/')
    .trim()
    .lowercase()
    .filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }

data class FeedState(
    val items: List<FeedItem> = emptyList(),
    val visibleCount: Int = 30,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val error: String? = null,
    val user: String = "",
    val safeOnly: Boolean = false,
    val blurNsfw: Boolean = true,
    val followedCount: Int = 0,
    val lastSyncAt: Long = 0L,
    val addedLastSync: Int = 0,
    /** Local search over the posts already loaded from the feed. */
    val query: String = "",
    /** Empty means all ratings, otherwise one of "general", "mature", "adult". */
    val rating: String = "",
    /** Lowercased usernames hidden from the feed. */
    val mutedUsers: Set<String> = emptySet(),
) {
    private val shown: List<FeedItem>
        get() = items.filter { item ->
            val matchesSafe = !safeOnly || !item.isNsfw
            val matchesMute = mutedUsers.none { it.equals(item.author, ignoreCase = true) }
            val matchesRating = rating.isBlank() || item.rating.equals(rating, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.author.contains(query, ignoreCase = true) ||
                item.authorDisplay.contains(query, ignoreCase = true)
            matchesSafe && matchesMute && matchesRating && matchesQuery
        }

    val visibleItems: List<FeedItem> get() = shown.take(visibleCount)
    val hasMore: Boolean get() = shown.size > visibleItems.size
    val totalShown: Int get() = shown.size
}

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val store = FeedStore(prefs)
    private val client = FaClient(userAgentProvider = { prefs.effectiveUserAgent }) { prefs.cookies }
    private val repository = FeedRepository(client, store) { prefs.user }

    var state by mutableStateOf(
        FeedState(
            items = store.loadItems(),
            user = prefs.user,
            safeOnly = prefs.safeOnly,
            blurNsfw = prefs.blurNsfw,
            followedCount = store.loadUsers().size,
            lastSyncAt = store.lastSyncAt,
            mutedUsers = prefs.mutedUsers,
        )
    )
        private set

    /** Set when FA answers with the login wall – the UI shows the login screen again. */
    var sessionExpired by mutableStateOf(false)
        private set

    /** Set when Cloudflare blocks the request; the UI offers a WebView to finish the check. */
    var blocked by mutableStateOf(false)
        private set

    private var started = false
    private var syncing = false

    fun start() {
        if (started) return
        started = true
        val stale = System.currentTimeMillis() - store.lastSyncAt > SYNC_TTL_MS
        when {
            state.items.isEmpty() -> refresh(full = true)
            stale -> refresh(full = false)
        }
    }

    fun refresh(full: Boolean = false) {
        if (syncing) return
        syncing = true
        val firstRun = state.items.isEmpty()
        state = state.copy(
            loading = firstRun,
            refreshing = !firstRun,
            error = null,
            progressDone = 0,
            progressTotal = 0,
        )
        viewModelScope.launch {
            try {
                val result = repository.sync(if (full || firstRun) SyncMode.FULL else SyncMode.QUICK) { done, total ->
                    viewModelScope.launch {
                        if (total > 0 && (done == 0 || done == total || done % 4 == 0)) {
                            state = state.copy(progressDone = done, progressTotal = total)
                        }
                    }
                }
                state = state.copy(
                    items = store.loadItems(),
                    loading = false,
                    refreshing = false,
                    error = null,
                    followedCount = result.totalUsers,
                    lastSyncAt = store.lastSyncAt,
                    addedLastSync = result.added,
                    progressDone = result.scannedUsers,
                    progressTotal = result.totalUsers,
                )
            } catch (t: Throwable) {
                if (t is FaAuthException) sessionExpired = true
                if (t is FaBlockedException) blocked = true
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = humanError(t),
                )
            } finally {
                syncing = false
            }
        }
    }

    fun syncAll() = refresh(full = true)

    fun loadMore() {
        state = state.copy(visibleCount = state.visibleCount + PAGE_SIZE)
    }

    fun setSafeOnly(value: Boolean) {
        prefs.safeOnly = value
        state = state.copy(safeOnly = value)
    }

    fun setBlurNsfw(value: Boolean) {
        prefs.blurNsfw = value
        state = state.copy(blurNsfw = value)
    }

    fun setQuery(value: String) {
        state = state.copy(query = value)
    }

    /** Passing the already selected rating clears the filter. */
    fun setRating(value: String) {
        state = state.copy(rating = if (state.rating.equals(value, ignoreCase = true)) "" else value)
    }

    fun muteUser(user: String) {
        val name = normalizeUser(user)
        if (name.isBlank()) return
        prefs.mutedUsers = prefs.mutedUsers + name
        state = state.copy(mutedUsers = prefs.mutedUsers)
    }

    fun unmuteUser(user: String) {
        prefs.mutedUsers = prefs.mutedUsers - user.lowercase()
        state = state.copy(mutedUsers = prefs.mutedUsers)
    }

    fun logout() {
        prefs.clearSession()
        state = FeedState(mutedUsers = prefs.mutedUsers)
        started = false
        syncing = false
        sessionExpired = true
        blocked = false
    }

    fun acknowledgeExpired() {
        sessionExpired = false
    }

    fun acknowledgeBlocked() {
        blocked = false
    }

    /** Resumes sync with cookies/UA obtained after the user solved the WebView challenge. */
    fun resolveChallenge(cookies: String, userAgent: String) {
        if (cookies.isBlank()) return
        prefs.cookies = cookies
        if (userAgent.isNotBlank()) prefs.userAgent = userAgent
        blocked = false
        refresh(full = false)
    }

    fun onLoggedIn(cookies: String, user: String, userAgent: String) {
        prefs.cookies = cookies
        prefs.user = user
        if (userAgent.isNotBlank()) prefs.userAgent = userAgent
        sessionExpired = false
        blocked = false
        state = FeedState(
            user = user,
            safeOnly = prefs.safeOnly,
            blurNsfw = prefs.blurNsfw,
            mutedUsers = prefs.mutedUsers,
        )
        started = false
        start()
    }

    private fun humanError(t: Throwable): String {
        val res = getApplication<Application>().resources
        return when (t) {
            is FaAuthException -> res.getString(R.string.session_expired)
            is FaBlockedException -> res.getString(R.string.sync_blocked)
            else -> res.getString(R.string.sync_failed, t.message ?: t::class.simpleName ?: "")
        }
    }

    companion object {
        const val PAGE_SIZE = 30
        const val SYNC_TTL_MS = 10 * 60 * 1000L
    }
}