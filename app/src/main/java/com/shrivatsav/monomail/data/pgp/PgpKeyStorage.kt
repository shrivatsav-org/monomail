package com.shrivatsav.monomail.data.pgp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.security.SecurityUtil
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PgpKeyStorage @Inject constructor(
    private val context: Context
) {
    // Encrypted metadata storage (fingerprint → PgpKeyInfo)
    private val gson = Gson()

    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "pgp_key_metadata",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val keysDir: File by lazy {
        File(context.filesDir, "pgp/keys").also { it.mkdirs() }
    }

    private val publicKeysDir: File by lazy {
        File(context.filesDir, "pgp/public").also { it.mkdirs() }
    }

    fun savePrivateKey(fingerprint: String, armoredKey: String) {
        val encrypted = SecurityUtil.encryptString(armoredKey)
        File(keysDir, "$fingerprint.asc").writeText(encrypted)
    }

    fun loadPrivateKey(fingerprint: String): String? {
        val file = File(keysDir, "$fingerprint.asc")
        if (!file.exists()) return null
        val encrypted = file.readText()
        return SecurityUtil.decryptString(encrypted)
    }

    fun deletePrivateKey(fingerprint: String) {
        File(keysDir, "$fingerprint.asc").delete()
    }

    fun savePublicKey(fingerprint: String, armoredKey: String) {
        File(publicKeysDir, "$fingerprint.asc").writeText(armoredKey)
    }

    fun loadPublicKey(fingerprint: String): String? {
        val file = File(publicKeysDir, "$fingerprint.asc")
        if (!file.exists()) return null
        return file.readText()
    }

    fun deletePublicKey(fingerprint: String) {
        File(publicKeysDir, "$fingerprint.asc").delete()
    }

    fun saveKeyMetadata(fingerprint: String, info: PgpKeyInfo) {
        val all = loadAllMetadata().toMutableMap()
        all[fingerprint] = info
        prefs.edit().putString(PREF_KEYS, gson.toJson(all)).commit()
    }

    fun loadKeyMetadata(fingerprint: String): PgpKeyInfo? {
        return loadAllMetadata().values.find { it.fingerprint == fingerprint }
    }

    fun loadAllMetadata(): Map<String, PgpKeyInfo> {
        val json = prefs.getString(PREF_KEYS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, PgpKeyInfo>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.w("PgpKeyStorage", "Failed to deserialize key metadata", e)
            emptyMap()
        }
    }

    fun deleteKeyMetadata(fingerprint: String) {
        val all = loadAllMetadata().toMutableMap()
        all.entries.removeIf { it.value.fingerprint == fingerprint }
        prefs.edit().putString(PREF_KEYS, gson.toJson(all)).commit()
    }

    fun keyFileExists(fingerprint: String): Boolean {
        return File(keysDir, "$fingerprint.asc").exists() ||
                File(publicKeysDir, "$fingerprint.asc").exists()
    }

    fun publicKeyExists(fingerprint: String): Boolean {
        return File(publicKeysDir, "$fingerprint.asc").exists()
    }

    fun privateKeyExists(fingerprint: String): Boolean {
        return File(keysDir, "$fingerprint.asc").exists()
    }

    fun savePassphrase(fingerprint: String, passphrase: String) {
        val encrypted = SecurityUtil.encryptString(passphrase)
        File(keysDir, "${fingerprint}_pass.enc").writeText(encrypted)
    }

    fun loadPassphrase(fingerprint: String): String? {
        val file = File(keysDir, "${fingerprint}_pass.enc")
        if (!file.exists()) return null
        val encrypted = file.readText()
        return SecurityUtil.decryptString(encrypted)
    }

    fun deletePassphrase(fingerprint: String) {
        File(keysDir, "${fingerprint}_pass.enc").delete()
    }

    fun isPassphraseProtected(fingerprint: String): Boolean {
        return File(keysDir, "${fingerprint}_pass.enc").exists()
    }

    companion object {
        private const val PREF_KEYS = "pgp_keys_metadata"
    }
}
