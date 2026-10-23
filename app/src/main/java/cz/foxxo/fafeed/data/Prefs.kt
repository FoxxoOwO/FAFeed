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

    fun clearSession() {
        sp.edit().remove("cookies").remove("user").remove("cached_feed_v2").apply()
    }
}
