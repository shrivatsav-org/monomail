package com.shrivatsav.monomail.security
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
@Suppress("DEPRECATION")
object SecurityUtil {
    private const val KEY_ALIAS = "monomail_data_keystore_alias"
    private const val PREFS_NAME = "monomail_secure_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IV_LENGTH = 12
    private fun getSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    fun getDatabasePassphrase(context: Context): CharArray {
        val prefs = getSecurePrefs(context)
        val stored = prefs.getString(DB_PASSPHRASE_KEY, null)
        if (stored != null) {
            val chars = stored.toCharArray()
            return chars
        }
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val passphrase = Base64.encodeToString(bytes, Base64.NO_WRAP)
        bytes.fill(0)
        prefs.edit().putString(DB_PASSPHRASE_KEY, passphrase).apply()
        val chars = passphrase.toCharArray()
        return chars
    }
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: generateSecretKey()
    }
    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    fun encryptString(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    fun decryptString(encryptedBase64: String): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) {
                android.util.Log.e("SecurityUtil", "decryptString: combined size < IV_LENGTH")
                return null
            }
            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            val spec = GCMParameterSpec(128, iv)
            val encryptedData = ByteArray(combined.size - iv.size)
            System.arraycopy(combined, iv.size, encryptedData, 0, encryptedData.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedData = cipher.doFinal(encryptedData)
            String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("SecurityUtil", "decryptString failed", e)
            null
        }
    }

    fun saveCustomGoogleClientId(context: Context, clientId: String) {
        val prefs = getSecurePrefs(context)
        prefs.edit().putString("custom_google_client_id", clientId).apply()
    }

    fun clearCustomGoogleClientId(context: Context) {
        val prefs = getSecurePrefs(context)
        prefs.edit().remove("custom_google_client_id").apply()
    }

    fun getGoogleClientId(context: Context): String {
        val prefs = getSecurePrefs(context)
        val customId = prefs.getString("custom_google_client_id", null)
        if (!customId.isNullOrBlank()) {
            return customId
        }
        return com.shrivatsav.monomail.BuildConfig.GOOGLE_CLIENT_ID
    }
}
