package cz.foxxo.fafeed.data

import android.content.Context
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fafeed", Context.MODE_PRIVATE)
    private val secure = SecureStorage(context)

    var cookies: String
        get() = secure.cookies
        set(value) { secure.cookies = value }

    var user: String
        get() = sp.getString("user", "").orEmpty()
        set(value) = sp.edit().putString("user", value).apply()

    /**
     * User-Agent of the WebView that completed the login. Cloudflare ties `cf_clearance`
     * to it, so every OkHttp/Coil/DownloadManager request has to send the same string.
     */
    var userAgent: String
        get() = sp.getString("user_agent", "").orEmpty()
        set(value) = sp.edit().putString("user_agent", value).apply()

    val effectiveUserAgent: String
        get() = userAgent.ifBlank { FA_USER_AGENT }

    var safeOnly: Boolean
        get() = sp.getBoolean("safe_only", false)
        set(value) = sp.edit().putBoolean("safe_only", value).apply()

    var blurNsfw: Boolean
        get() = sp.getBoolean("blur_nsfw", true)
        set(value) = sp.edit().putBoolean("blur_nsfw", value).apply()

    var cachedFeed: String
        get() = sp.getString("cached_feed_v2", "").orEmpty()
        set(value) = sp.edit().putString("cached_feed_v2", value).apply()

    var downloadTreeUri: String
        get() = sp.getString("download_tree_uri", "").orEmpty()
        set(value) = sp.edit().putString("download_tree_uri", value).apply()

    var downloadTreeName: String
        get() = sp.getString("download_tree_name", "").orEmpty()
        set(value) = sp.edit().putString("download_tree_name", value).apply()

    var watchUsers: String
        get() = sp.getString("watch_users", "").orEmpty()
        set(value) = sp.edit().putString("watch_users", value).apply()

    var scanState: String
        get() = sp.getString("scan_state", "").orEmpty()
        set(value) = sp.edit().putString("scan_state", value).apply()

    var scanIndex: Int
        get() = sp.getInt("scan_index", 0)
        set(value) = sp.edit().putInt("scan_index", value).apply()

    var lastSyncAt: Long
        get() = sp.getLong("last_sync_at", 0L)
        set(value) = sp.edit().putLong("last_sync_at", value).apply()

    /** Highest submission id the user has already seen; posts above it are shown as new. */
    var unreadBaselineId: Long
        get() = sp.getLong("unread_baseline_id", 0L)
        set(value) = sp.edit().putLong("unread_baseline_id", value).apply()

    /** Individually opened posts among the unread batch. */
    var readIds: Set<Long>
        get() = sp.getString("read_ids", "").orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        set(value) = sp.edit().putString("read_ids", value.joinToString(",")).apply()

    var usersFetchedAt: Long
        get() = sp.getLong("users_fetched_at", 0L)
        set(value) = sp.edit().putLong("users_fetched_at", value).apply()

    /** "system" follows the device locale, otherwise a language tag such as "cs" or "en". */
    var lang: String
        get() = sp.getString("lang", "system").orEmpty()
        set(value) = sp.edit().putString("lang", value).apply()

    /** "system", "light" or "dark" – see THEME_* constants in Theme.kt. */
    var theme: String
        get() = sp.getString("theme", "system").orEmpty()
        set(value) = sp.edit().putString("theme", value).apply()

    /** Material You colours from the wallpaper; only applied on Android 12+. */
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", false)
        set(value) = sp.edit().putBoolean("dynamic_color", value).apply()

    /** Username of the account currently in use. */
    var activeAccount: String
        get() = sp.getString("active_account", "").orEmpty()
        set(value) = sp.edit().putString("active_account", value).apply()

    /** Per-account feed snapshots, keyed by lowercased username. */
    var accountSnapshots: String
        get() = sp.getString("account_snapshots", "").orEmpty()
        set(value) = sp.edit().putString("account_snapshots", value).apply()

    /** Feed-related state of the active account, stored when switching away from it. */
    fun snapshot(): String = JSONObject()
        .put("cached_feed_v2", cachedFeed)
        .put("watch_users", watchUsers)
        .put("scan_state", scanState)
        .put("scan_index", scanIndex)
        .put("last_sync_at", lastSyncAt)
        .put("users_fetched_at", usersFetchedAt)
        .put("unread_baseline_id", unreadBaselineId)
        .put("read_ids", readIds.joinToString(","))
        .put("muted_users", mutedUsers.joinToString("\n"))
        .toString()

    fun restoreSnapshot(json: String) {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return
        cachedFeed = root.optString("cached_feed_v2")
        watchUsers = root.optString("watch_users")
        scanState = root.optString("scan_state")
        scanIndex = root.optInt("scan_index")
        lastSyncAt = root.optLong("last_sync_at")
        usersFetchedAt = root.optLong("users_fetched_at")
        unreadBaselineId = root.optLong("unread_baseline_id")
        val ids = root.optString("read_ids")
        sp.edit().putString("read_ids", ids).apply()
        mutedUsers = root.optString("muted_users")
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** Automatic update check once a day on app start. Enabled by default. */
    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(value) = sp.edit().putBoolean("auto_update", value).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) = sp.edit().putLong("last_update_check", value).apply()

    /** Tag of a release the user chose to skip; it will not be offered automatically again. */
    var skippedUpdateVersion: String
        get() = sp.getString("skipped_update", "").orEmpty()
        set(value) = sp.edit().putString("skipped_update", value).apply()

    /** Lowercased usernames whose posts are hidden from the feed. */
    var mutedUsers: Set<String>
        get() = sp.getStringSet("muted_users", emptySet())?.toSet().orEmpty()
        set(value) = sp.edit().putStringSet("muted_users", value).apply()

    fun clearSession() {
        secure.clearCookies()
        sp.edit().remove("cookies").remove("user").remove("cached_feed_v2")
            .remove("watch_users").remove("scan_state").remove("scan_index")
            .remove("last_sync_at").remove("users_fetched_at")
            .remove("unread_baseline_id").remove("read_ids").apply()
    }
}
