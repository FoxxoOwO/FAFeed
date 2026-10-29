package cz.foxxo.fafeed.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds the feed from the watchlist itself instead of the submission notifications,
 * so clearing notifications (or FA pruning them after 6 months) no longer empties the feed.
 *
 * 1. watchlist -> usernames (cached 24 h)
 * 2. per user: gallery page 1 (newest 48 cards) -> everything newer than our cursor
 * 3. notification queue as a cheap fast lane while it still has items
 */
class FeedRepository(
    private val client: FaClient,
    private val store: FeedStore,
    private val usernameProvider: () -> String,
) {

    suspend fun sync(
        mode: SyncMode,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncResult = coroutineScope {
        val now = System.currentTimeMillis()
        var users = store.loadUsers()
        if (users.isEmpty() || now - store.usersFetchedAt > USERS_TTL_MS) {
            val fetched = runCatching { fetchWatchlist() }.getOrElse { throw it }
            if (fetched.isNotEmpty()) {
                users = fetched
                store.saveUsers(users)
                store.usersFetchedAt = now
            }
        }
        if (users.isEmpty()) return@coroutineScope SyncResult()

        val known = store.loadItems().associateBy { it.id }
        val incoming = mutableListOf<FeedItem>()

        // Fast lane: only exists until the user clears/prunes their notifications.
        runCatching { client.feed(FEED_URL) }
            .getOrNull()
            ?.let { page ->
                if (page.loginRequired) throw FaAuthException()
                incoming += page.items
            }

        val scans = store.loadScans().toMutableMap()
        val selection = FeedPlanner.select(users, scans, mode, store.scanIndex, QUICK_WINDOW)
        onProgress(0, selection.size)

        val semaphore = Semaphore(MAX_PARALLEL)
        val done = AtomicInteger(0)
        val errors = AtomicInteger(0)

        val results = selection.map { user ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val previous = scans[user]
                    val outcome = runCatching { scanUser(user, previous) }
                    outcome.exceptionOrNull()?.let { error ->
                        if (error is FaAuthException) throw error
                        errors.incrementAndGet()
                    }
                    onProgress(done.incrementAndGet(), selection.size)
                    outcome.getOrNull() ?: (user to null)
                }
            }
        }.awaitAll()

        results.forEach { (user, outcome) ->
            if (outcome == null) return@forEach
            incoming += outcome.items
            scans[user] = outcome.scan
        }

        val merged = FeedMerge.merge(known.values + incoming, emptyList(), LIMIT)
        store.saveItems(merged)
        store.saveScans(scans)
        store.lastSyncAt = System.currentTimeMillis()

        val index = store.scanIndex
        store.scanIndex = if (users.isEmpty()) 0 else (index + selection.size) % users.size

        SyncResult(
            added = incoming.count { it.id !in known },
            scannedUsers = selection.size - errors.get(),
            totalUsers = users.size,
            errors = errors.get(),
        )
    }

    private data class ScanOutcome(val items: List<FeedItem>, val scan: UserScan)

    private suspend fun scanUser(user: String, previous: UserScan?): Pair<String, ScanOutcome?> {
        val url = "${FaParser.BASE}/gallery/$user/"
        val html = client.getHtml(url)
        val figures = FaParser.parseFigures(html, url)
        val fresh = FeedMerge.takeNew(figures, previous, SEED_PER_USER)
        val scan = UserScan(
            lastId = maxOf(FeedMerge.newestId(figures), previous?.lastId ?: 0L),
            scannedAt = System.currentTimeMillis(),
        )
        return user to ScanOutcome(fresh, scan)
    }

    private suspend fun fetchWatchlist(): List<String> {
        val me = usernameProvider().trim()
        if (me.isBlank()) throw FaAuthException()
        val users = LinkedHashSet<String>()
        var url: String? = "${FaParser.BASE}/watchlist/by/$me/"
        var page = 0
        while (url != null && page < MAX_WATCHLIST_PAGES) {
            val html = client.getHtml(url)
            val parsed = FaParser.parseWatchlist(html, url)
            users += parsed.users
            url = parsed.nextUrl
            page++
        }
        return users.toList()
    }

    companion object {
        const val FEED_URL = "https://www.furaffinity.net/msg/submissions/"
        const val QUICK_WINDOW = 60
        const val MAX_PARALLEL = 6
        const val SEED_PER_USER = 3
        const val LIMIT = 800
        const val MAX_WATCHLIST_PAGES = 6
        const val USERS_TTL_MS = 24 * 60 * 60 * 1000L
    }
}