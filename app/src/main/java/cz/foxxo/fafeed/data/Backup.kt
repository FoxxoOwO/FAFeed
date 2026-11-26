package cz.foxxo.fafeed.data

import org.json.JSONObject

/** Portable settings backup. Session cookies are intentionally never exported. */
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val safeOnly: Boolean = false,
    val blurNsfw: Boolean = true,
    val lang: String = "system",
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val watchUsers: List<String> = emptyList(),
    val mutedUsers: Set<String> = emptySet(),
    val accountNames: List<String> = emptyList(),
    val downloadTreeUri: String = "",
    val downloadTreeName: String = "",
)

object Backup {
    fun serialize(data: BackupData): String {
        val root = JSONObject()
            .put("version", data.version)
            .put("exportedAt", data.exportedAt)
            .put("safeOnly", data.safeOnly)
            .put("blurNsfw", data.blurNsfw)
            .put("lang", data.lang)
            .put("theme", data.theme)
            .put("dynamicColor", data.dynamicColor)
            .put("watchUsers", data.watchUsers.joinToString("\n"))
            .put("mutedUsers", data.mutedUsers.joinToString("\n"))
            .put("accountNames", data.accountNames.joinToString("\n"))
            .put("downloadTreeUri", data.downloadTreeUri)
            .put("downloadTreeName", data.downloadTreeName)
        return root.toString(2)
    }

    fun parse(json: String): BackupData? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("version", -1) != 1) return null
        return BackupData(
            exportedAt = root.optLong("exportedAt"),
            safeOnly = root.optBoolean("safeOnly"),
            blurNsfw = root.optBoolean("blurNsfw", true),
            lang = root.optString("lang").ifBlank { "system" },
            theme = root.optString("theme").ifBlank { "system" },
            dynamicColor = root.optBoolean("dynamicColor"),
            watchUsers = lines(root.optString("watchUsers")),
            mutedUsers = lines(root.optString("mutedUsers")).toSet(),
            accountNames = lines(root.optString("accountNames")),
            downloadTreeUri = root.optString("downloadTreeUri"),
            downloadTreeName = root.optString("downloadTreeName"),
        )
    }

    private fun lines(raw: String): List<String> =
        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
}
