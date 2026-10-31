package cz.foxxo.fafeed.ui

import cz.foxxo.fafeed.R
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.foxxo.fafeed.data.FaAuthException
import cz.foxxo.fafeed.data.FaClient
import cz.foxxo.fafeed.data.FeedItem
import cz.foxxo.fafeed.data.FeedRepository
import cz.foxxo.fafeed.data.FeedStore
import cz.foxxo.fafeed.data.Prefs
import cz.foxxo.fafeed.data.SyncMode
import kotlinx.coroutines.launch

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
) {
    private val shown: List<FeedItem>
        get() = if (safeOnly) items.filter { !it.isNsfw } else items

    val visibleItems: List<FeedItem> get() = shown.take(visibleCount)
    val hasMore: Boolean get() = shown.size > visibleItems.size
    val totalShown: Int get() = shown.size
}

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val store = FeedStore(prefs)
    private val client = FaClient { prefs.cookies }
    private val repository = FeedRepository(client, store) { prefs.user }

    var state by mutableStateOf(
        FeedState(
            items = store.loadItems(),
            user = prefs.user,
            safeOnly = prefs.safeOnly,
            blurNsfw = prefs.blurNsfw,
            followedCount = store.loadUsers().size,
            lastSyncAt = store.lastSyncAt,
        )
    )
        private set

    /** Set when FA answers with the login wall – the UI shows the login screen again. */
    var sessionExpired by mutableStateOf(false)
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

    fun logout() {
        prefs.clearSession()
        state = FeedState()
        started = false
        syncing = false
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
        started = false
        start()
    }

    private fun humanError(t: Throwable): String {
        val res = getApplication<Application>().resources
        return when (t) {
            is FaAuthException -> res.getString(R.string.session_expired)
            else -> res.getString(R.string.sync_failed, t.message ?: t::class.simpleName ?: "")
        }
    }

    companion object {
        const val PAGE_SIZE = 30
        const val SYNC_TTL_MS = 10 * 60 * 1000L
    }
}