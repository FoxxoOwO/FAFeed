package cz.foxxo.fafeed.data

import android.content.Context
import org.json.JSONObject

data class AccountCredentials(
    val user: String,
    val cookies: String,
    val userAgent: String,
)

/** Several FA logins, stored encrypted. The feed cache itself is rebuilt on switch. */
class Accounts(context: Context) {
    private val secure = SecureStorage(context)
    private val prefs = Prefs(context)

    private fun all(): MutableMap<String, AccountCredentials> {
        val result = linkedMapOf<String, AccountCredentials>()
        val root = runCatching { JSONObject(secure.accountsJson) }.getOrNull() ?: return result
        root.keys().forEach { key ->
            val obj = root.optJSONObject(key) ?: return@forEach
            val user = obj.optString("user").ifBlank { key }
            if (user.isBlank()) return@forEach
            result[user.lowercase()] = AccountCredentials(
                user = user,
                cookies = obj.optString("cookies"),
                userAgent = obj.optString("userAgent"),
            )
        }
        return result
    }

    private fun saveAll(accounts: Map<String, AccountCredentials>) {
        val root = JSONObject()
        accounts.values.forEach { account ->
            root.put(
                account.user.lowercase(),
                JSONObject()
                    .put("user", account.user)
                    .put("cookies", account.cookies)
                    .put("userAgent", account.userAgent),
            )
        }
        secure.accountsJson = root.toString()
    }

    var active: String
        get() = prefs.activeAccount
        set(value) { prefs.activeAccount = value }

    fun list(): List<AccountCredentials> = all().values.sortedBy { it.user.lowercase() }

    fun get(user: String): AccountCredentials? = all()[user.lowercase()]

    fun save(account: AccountCredentials) {
        if (account.user.isBlank()) return
        val accounts = all()
        accounts[account.user.lowercase()] = account
        saveAll(accounts)
    }

    private fun snapshots(): MutableMap<String, String> {
        val result = linkedMapOf<String, String>()
        val root = runCatching { JSONObject(prefs.accountSnapshots) }.getOrNull() ?: return result
        root.keys().forEach { key -> result[key.lowercase()] = root.optString(key) }
        return result
    }

    fun saveSnapshot(user: String, json: String) {
        if (user.isBlank()) return
        val map = snapshots()
        map[user.lowercase()] = json
        val root = JSONObject()
        map.forEach { (key, value) -> root.put(key, value) }
        prefs.accountSnapshots = root.toString()
    }

    fun snapshot(user: String): String? = snapshots()[user.lowercase()]

    fun removeSnapshot(user: String) {
        val map = snapshots()
        map.remove(user.lowercase())
        val root = JSONObject()
        map.forEach { (key, value) -> root.put(key, value) }
        prefs.accountSnapshots = root.toString()
    }

    fun remove(user: String) {
        val accounts = all()
        accounts.remove(user.lowercase())
        saveAll(accounts)
        removeSnapshot(user)
        if (active.equals(user, ignoreCase = true)) active = ""
    }

    /** Imports the session from a version that had no multi-account support. */
    fun ensureCurrent() {
        val user = prefs.user
        if (user.isBlank() || prefs.cookies.isBlank()) return
        if (get(user) == null) save(AccountCredentials(user, prefs.cookies, prefs.userAgent))
        if (active.isBlank()) active = user
    }
}
