package cz.foxxo.fafeed.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted key/value storage for sensitive data (FA cookies and the account list).
 * Values written by older versions in plain SharedPreferences are migrated on first read.
 * If the device cannot provide a Keystore key, the plain file is used as a fallback.
 */
@Suppress("DEPRECATION")
class SecureStorage(context: Context) {
    private val app = context.applicationContext
    private val plain = app.getSharedPreferences("fafeed", Context.MODE_PRIVATE)

    private val encrypted: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            "fafeed_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Throwable) {
        null
    }

    fun read(key: String): String? {
        val secured = encrypted?.getString(key, null)
        if (!secured.isNullOrEmpty()) return secured
        val legacy = plain.getString(key, null)
        if (!legacy.isNullOrEmpty()) {
            write(key, legacy)
        }
        return legacy
    }

    fun write(key: String, value: String?) {
        if (encrypted != null) {
            encrypted.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            plain.edit().remove(key).apply()
        } else {
            plain.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }

    var cookies: String
        get() = read(KEY_COOKIES).orEmpty()
        set(value) = write(KEY_COOKIES, value.ifBlank { null })

    fun clearCookies() = write(KEY_COOKIES, null)

    var accountsJson: String
        get() = read(KEY_ACCOUNTS).orEmpty()
        set(value) = write(KEY_ACCOUNTS, value.ifBlank { null })

    private companion object {
        const val KEY_COOKIES = "cookies"
        const val KEY_ACCOUNTS = "accounts"
    }
}
