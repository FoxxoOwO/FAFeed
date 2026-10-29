package cz.foxxo.fafeed.data

import org.json.JSONArray
import org.json.JSONObject

/** Per-user sync cursor: newest submission id we have already pulled + when we checked. */
data class UserScan(val lastId: Long, val scannedAt: Long)

/**
 * Everything the aggregated (notification-free) feed needs to survive restarts:
 * merged submission cache, watched users, per-user cursors and the rotation index.
 */
class FeedStore(private val prefs: Prefs) {

    fun loadItems(): List<FeedItem> = parseItems(prefs.cachedFeed)

    fun saveItems(items: List<FeedItem>) {
        prefs.cachedFeed = itemsToJson(items)
    }

    fun loadUsers(): List<String> {
        val raw = prefs.watchUsers
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { name -> name.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    fun saveUsers(users: List<String>) {
        prefs.watchUsers = JSONArray(users).toString()
    }

    var usersFetchedAt: Long
        get() = prefs.usersFetchedAt
        set(value) {
            prefs.usersFetchedAt = value
        }

    fun loadScans(): Map<String, UserScan> {
        val raw = prefs.scanState
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { key ->
                val entry = obj.optJSONObject(key)
                UserScan(entry?.optLong("lastId", 0L) ?: 0L, entry?.optLong("at", 0L) ?: 0L)
            }
        }.getOrDefault(emptyMap())
    }

    fun saveScans(scans: Map<String, UserScan>) {
        val obj = JSONObject()
        scans.forEach { (user, scan) ->
            obj.put(user, JSONObject().put("lastId", scan.lastId).put("at", scan.scannedAt))
        }
        prefs.scanState = obj.toString()
    }

    var scanIndex: Int
        get() = prefs.scanIndex
        set(value) {
            prefs.scanIndex = value
        }

    var lastSyncAt: Long
        get() = prefs.lastSyncAt
        set(value) {
            prefs.lastSyncAt = value
        }

    companion object {
        fun itemsToJson(items: List<FeedItem>): String {
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
            return array.toString()
        }

        fun parseItems(raw: String): List<FeedItem> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
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
                }.filter { it.id > 0L }
            }.getOrDefault(emptyList())
        }
    }
}

enum class SyncMode { QUICK, FULL }

data class SyncResult(
    val added: Int = 0,
    val scannedUsers: Int = 0,
    val totalUsers: Int = 0,
    val errors: Int = 0,
)

/** Pure helpers – unit tested without Android. */
object FeedPlanner {

    /** Users to (re)scan in this sync. FULL = everybody, stalest first. QUICK = rotating window. */
    fun select(
        users: List<String>,
        scans: Map<String, UserScan>,
        mode: SyncMode,
        startIndex: Int,
        quickWindow: Int,
    ): List<String> {
        if (users.isEmpty()) return emptyList()
        if (mode == SyncMode.FULL) {
            return users.sortedBy { scans[it]?.scannedAt ?: 0L }
        }
        val n = users.size
        val index = ((startIndex % n) + n) % n
        val rotated = users.drop(index) + users.take(index)
        return rotated.take(quickWindow.coerceAtLeast(1).coerceAtMost(n))
    }
}

object FeedMerge {

    /** Dedupe by submission id, newest first, capped. */
    fun merge(existing: Collection<FeedItem>, incoming: Collection<FeedItem>, limit: Int): List<FeedItem> {
        val map = LinkedHashMap<Long, FeedItem>(existing.size + incoming.size)
        existing.forEach { map[it.id] = it }
        incoming.forEach { map[it.id] = it }
        return map.values.sortedByDescending { it.id }.take(limit)
    }

    /** Items this user has published since our cursor. First scan seeds only the newest few. */
    fun takeNew(items: List<FeedItem>, previous: UserScan?, seedPerUser: Int): List<FeedItem> {
        val sorted = items.sortedByDescending { it.id }
        if (previous == null || previous.lastId <= 0L) return sorted.take(seedPerUser)
        return sorted.filter { it.id > previous.lastId }
    }

    fun newestId(items: List<FeedItem>): Long = items.maxOfOrNull { it.id } ?: 0L
}