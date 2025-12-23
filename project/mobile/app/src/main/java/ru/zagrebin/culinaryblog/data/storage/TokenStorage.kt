package ru.zagrebin.culinaryblog.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences

import androidx.core.content.edit
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class TokenStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit { putString("access_token", token) }
    }

    fun saveUserId(userId: Long) {
        prefs.edit { putLong("user_id", userId) }
    }

    fun getUserId(): Long? {
        val id = prefs.getLong("user_id", -1)
        return if (id == -1L) null else id
    }

    fun clearToken() {
        prefs.edit { 
            remove("access_token")
            remove("user_id")
        }
    }

    // Backward-compatible alias, kept to avoid crashes if called from old code paths
    fun cleatToken() = clearToken()

    fun getToken(): String? = prefs.getString("access_token", null)
}
