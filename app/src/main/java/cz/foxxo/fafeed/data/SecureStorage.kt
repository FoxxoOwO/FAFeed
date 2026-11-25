package cz.foxxo.fafeed.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the FurAffinity session cookie encrypted at rest.
 *
 * Existing installs had the cookie in plain SharedPreferences; on first read it is
 * migrated and removed from the plain file. If the device cannot provide a Keystore
 * key, the plain file is used as a fallback so the app stays usable.
 */
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

    var cookies: String
        get() {
            val secured = encrypted?.getString(KEY, null)
            if (!secured.isNullOrEmpty()) return secured
            val legacy = plain.getString(KEY, "").orEmpty()
            if (legacy.isNotBlank()) {
                encrypted?.edit()?.putString(KEY, legacy)?.apply()
                plain.edit().remove(KEY).apply()
            }
            return legacy
        }
        set(value) {
            if (value.isBlank()) {
                clear()
                return
            }
            if (encrypted != null) {
                encrypted.edit().putString(KEY, value).apply()
                plain.edit().remove(KEY).apply()
            } else {
                plain.edit().putString(KEY, value).apply()
            }
        }

    fun clear() {
        encrypted?.edit()?.remove(KEY)?.apply()
        plain.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "cookies"
    }
}
