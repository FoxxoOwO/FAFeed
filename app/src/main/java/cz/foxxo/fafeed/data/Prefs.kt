package cz.foxxo.fafeed.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fafeed", Context.MODE_PRIVATE)

    var cookies: String
        get() = sp.getString("cookies", "").orEmpty()
        set(value) = sp.edit().putString("cookies", value).apply()

    var user: String
        get() = sp.getString("user", "").orEmpty()
        set(value) = sp.edit().putString("user", value).apply()

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

    var usersFetchedAt: Long
        get() = sp.getLong("users_fetched_at", 0L)
        set(value) = sp.edit().putLong("users_fetched_at", value).apply()

    /** "system" follows the device locale, otherwise a language tag such as "cs" or "en". */
    var lang: String
        get() = sp.getString("lang", "system").orEmpty()
        set(value) = sp.edit().putString("lang", value).apply()

    fun clearSession() {
        sp.edit().remove("cookies").remove("user").remove("cached_feed_v2")
            .remove("watch_users").remove("scan_state").remove("scan_index")
            .remove("last_sync_at").remove("users_fetched_at").apply()
    }
}
