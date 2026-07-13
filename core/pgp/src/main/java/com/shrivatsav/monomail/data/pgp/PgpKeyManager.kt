package com.shrivatsav.monomail.data.pgp

import android.util.Log
import org.bouncycastle.openpgp.api.OpenPGPCertificate
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.KeyFlag
import org.pgpainless.algorithm.PublicKeyAlgorithm
import org.pgpainless.key.OpenPgpFingerprint
import org.pgpainless.key.generation.KeySpec
import org.pgpainless.key.generation.type.ecc.Ed25519
import org.pgpainless.key.generation.type.ecc.X25519
import org.pgpainless.key.info.KeyRingInfo
import org.pgpainless.key.parsing.KeyRingReader
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PgpKeyManager @Inject constructor(
    private val storage: PgpKeyStorage
) {
    fun generateKeyPair(userId: String, passphrase: String? = null): PgpKeyInfo {
        val keySpec = KeySpec.getBuilder(Ed25519(), KeyFlag.SIGN_DATA, KeyFlag.CERTIFY_OTHER)
            .build()
        val subkeySpec = KeySpec.getBuilder(X25519(), KeyFlag.ENCRYPT_COMMS, KeyFlag.ENCRYPT_STORAGE)
            .build()

        val builder = PGPainless.getInstance().buildKey()
            .setPrimaryKey(keySpec)
            .addSubkey(subkeySpec)
            .addUserId(userId)

        if (passphrase != null) {
            builder.setPassphrase(Passphrase.fromPassword(passphrase))
        }

        val openPgpKey = builder.build()
        val secretKeyRing = openPgpKey.getPGPKeyRing() as org.bouncycastle.openpgp.PGPSecretKeyRing
        val publicKeyRing = openPgpKey.getPGPPublicKeyRing()

        val info = keyRingToInfo(
            secretKeyRing, isPrivate = true,
            isPassphraseProtected = passphrase != null
        )

        val armoredSecret = openPgpKey.toAsciiArmoredString()
        storage.savePrivateKey(info.fingerprint, armoredSecret)

        val armoredPublic = OpenPGPCertificate(publicKeyRing).toAsciiArmoredString()
        storage.savePublicKey(info.fingerprint, armoredPublic)

        storage.saveKeyMetadata(info.fingerprint, info)
        if (passphrase != null) {
            storage.savePassphrase(info.fingerprint, passphrase)
        }
        return info
    }

    fun importKey(armoredKey: String, passphrase: String? = null): PgpKeyInfo {
        val isPrivate = armoredKey.contains("BEGIN PGP PRIVATE KEY BLOCK")

        val info = if (isPrivate) {
            val secretKeyRing = KeyRingReader().secretKeyRing(armoredKey)
                ?: throw IllegalArgumentException("Failed to parse private key — invalid or corrupted key data")
            val isProtected = try {
                secretKeyRing.secretKey.keyEncryptionAlgorithm != 0
            } catch (_: Exception) { false }

            val keyInfo = keyRingToInfo(secretKeyRing, isPrivate = true, isPassphraseProtected = isProtected)

            storage.savePrivateKey(keyInfo.fingerprint, armoredKey)
            val publicKey = PGPainless.getInstance().toKey(secretKeyRing)
            val publicKeyRing = publicKey.getPGPPublicKeyRing()
            val armoredPublic = OpenPGPCertificate(publicKeyRing).toAsciiArmoredString()
            storage.savePublicKey(keyInfo.fingerprint, armoredPublic)
            storage.saveKeyMetadata(keyInfo.fingerprint, keyInfo)
            if (passphrase != null) {
                storage.savePassphrase(keyInfo.fingerprint, passphrase)
            }
            keyInfo
        } else {
            val publicKeyRing = KeyRingReader().publicKeyRing(armoredKey)
                ?: throw IllegalArgumentException("Failed to parse public key — invalid or corrupted key data")
            val keyInfo = keyRingToInfo(publicKeyRing, isPrivate = false)

            storage.savePublicKey(keyInfo.fingerprint, armoredKey)
            storage.saveKeyMetadata(keyInfo.fingerprint, keyInfo)
            keyInfo
        }
        return info
    }

    fun exportPublicKey(fingerprint: String): String? {
        return storage.loadPublicKey(fingerprint)
    }

    fun listKeys(): List<PgpKeyInfo> {
        return storage.loadAllMetadata().values.toList()
    }

    fun deleteKey(fingerprint: String) {
        storage.deletePrivateKey(fingerprint)
        storage.deletePublicKey(fingerprint)
        storage.deleteKeyMetadata(fingerprint)
    }

    private fun extractEmailFromUserId(userId: String): String? {
        // PGP User ID format: "Display Name <email@domain>" or just "email@domain"
        val angleBracket = userId.lastIndexOf('<')
        val closeAngle = userId.lastIndexOf('>')
        if (angleBracket != -1 && closeAngle > angleBracket) {
            return userId.substring(angleBracket + 1, closeAngle).trim()
        }
        // No angle brackets — the whole string might be an email address
        val trimmed = userId.trim()
        return if ('@' in trimmed) trimmed else null
    }

    fun getPublicKeyForRecipient(email: String): ByteArray? {
        val allKeys = listKeys().filter { info ->
            val extracted = extractEmailFromUserId(info.userId)
            extracted != null && extracted.equals(email, ignoreCase = true)
        }
        for (info in allKeys) {
            val armored = storage.loadPublicKey(info.fingerprint) ?: continue
            try {
                val publicKeyRing = KeyRingReader().publicKeyRing(armored)
                return publicKeyRing!!.encoded
            } catch (e: Exception) {
                Log.w("PgpKeyManager", "Failed to parse public key ring for recipient", e)
                continue
            }
        }
        return null
    }

    fun getPublicKeyForRecipientAsFingerprint(email: String): String? {
        val allKeys = listKeys().filter { info ->
            val extracted = extractEmailFromUserId(info.userId)
            extracted != null && extracted.equals(email, ignoreCase = true)
        }
        for (info in allKeys) {
            val armored = storage.loadPublicKey(info.fingerprint) ?: continue
            try {
                KeyRingReader().publicKeyRing(armored)
                return info.fingerprint
            } catch (e: Exception) {
                Log.w("PgpKeyManager", "Failed to parse public key ring for fingerprint lookup", e)
                continue
            }
        }
        return null
    }

    fun loadSecretKeyRing(fingerprint: String): org.bouncycastle.openpgp.PGPSecretKeyRing? {
        val armored = storage.loadPrivateKey(fingerprint) ?: return null
        return try {
            KeyRingReader().secretKeyRing(armored)
        } catch (e: Exception) {
            Log.w("PgpKeyManager", "Failed to load secret key ring for $fingerprint", e)
            null
        }
    }

    fun loadPublicKeyRing(fingerprint: String): org.bouncycastle.openpgp.PGPPublicKeyRing? {
        val armored = storage.loadPublicKey(fingerprint) ?: return null
        return try {
            KeyRingReader().publicKeyRing(armored)
        } catch (e: Exception) {
            Log.w("PgpKeyManager", "Failed to load public key ring for $fingerprint", e)
            null
        }
    }

    private fun keyRingToInfo(
        ring: org.bouncycastle.openpgp.PGPSecretKeyRing,
        isPrivate: Boolean,
        isPassphraseProtected: Boolean = false
    ): PgpKeyInfo {
        val info = KeyRingInfo(ring)
        return PgpKeyInfo(
            fingerprint = info.fingerprint.toString(),
            userId = info.primaryUserId ?: "Unknown",
            algorithm = info.algorithm.name,
            creationDate = info.creationDate.time,
            isPrivate = isPrivate,
            isExpired = info.primaryKeyExpirationDate?.let { it.before(Date()) } ?: false,
            isPassphraseProtected = isPassphraseProtected
        )
    }

    private fun keyRingToInfo(
        ring: org.bouncycastle.openpgp.PGPPublicKeyRing,
        isPrivate: Boolean
    ): PgpKeyInfo {
        val info = KeyRingInfo(ring)
        return PgpKeyInfo(
            fingerprint = info.fingerprint.toString(),
            userId = info.primaryUserId ?: "Unknown",
            algorithm = info.algorithm.name,
            creationDate = info.creationDate.time,
            isPrivate = isPrivate,
            isExpired = info.primaryKeyExpirationDate?.let { it.before(Date()) } ?: false
        )
    }
}
