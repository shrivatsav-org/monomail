package com.shrivatsav.monomail.data.pgp

import org.pgpainless.PGPainless
import org.pgpainless.algorithm.KeyFlag
import org.pgpainless.algorithm.PublicKeyAlgorithm
import org.pgpainless.key.OpenPgpFingerprint
import org.pgpainless.key.generation.KeySpec
import org.pgpainless.key.generation.type.ecc.Ed25519
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
        val subkeySpec = KeySpec.getBuilder(Ed25519(), KeyFlag.ENCRYPT_COMMS, KeyFlag.ENCRYPT_STORAGE)
            .build()

        val builder = PGPainless.buildKeyRing()
            .setPrimaryKey(keySpec)
            .addSubkey(subkeySpec)
            .addUserId(userId)

        if (passphrase != null) {
            builder.setPassphrase(Passphrase.fromPassword(passphrase))
        }

        val openPgpKey = builder.build()
        val secretKeyRing = openPgpKey.pgpSecretKeyRing
        val publicKeyRing = PGPainless.extractCertificate(secretKeyRing)

        val info = keyRingToInfo(secretKeyRing, isPrivate = true)

        val armoredSecret = PGPainless.asciiArmor(secretKeyRing)
        storage.savePrivateKey(info.fingerprint, armoredSecret)

        val armoredPublic = PGPainless.asciiArmor(publicKeyRing)
        storage.savePublicKey(info.fingerprint, armoredPublic)

        storage.saveKeyMetadata(info.fingerprint, info)
        return info
    }

    fun importKey(armoredKey: String, passphrase: String? = null): PgpKeyInfo {
        val isPrivate = armoredKey.contains("BEGIN PGP PRIVATE KEY BLOCK")

        if (isPrivate) {
            val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(armoredKey)
            val info = keyRingToInfo(secretKeyRing!!, isPrivate = true)

            storage.savePrivateKey(info.fingerprint, armoredKey)
            val publicKeyRing = PGPainless.extractCertificate(secretKeyRing!!)
            val armoredPublic = PGPainless.asciiArmor(publicKeyRing)
            storage.savePublicKey(info.fingerprint, armoredPublic)
            storage.saveKeyMetadata(info.fingerprint, info)
            return info
        } else {
            val publicKeyRing = PGPainless.readKeyRing().publicKeyRing(armoredKey)
            val info = keyRingToInfo(publicKeyRing!!, isPrivate = false)

            storage.savePublicKey(info.fingerprint, armoredKey)
            storage.saveKeyMetadata(info.fingerprint, info)
            return info
        }
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

    fun getPublicKeyForRecipient(email: String): ByteArray? {
        val allKeys = listKeys().filter { it.userId.contains(email, ignoreCase = true) }
        for (info in allKeys) {
            val armored = storage.loadPublicKey(info.fingerprint) ?: continue
            try {
                val publicKeyRing = PGPainless.readKeyRing().publicKeyRing(armored)
                return publicKeyRing!!.encoded
            } catch (_: Exception) { continue }
        }
        return null
    }

    fun getPublicKeyForRecipientAsFingerprint(email: String): String? {
        val allKeys = listKeys().filter { it.userId.contains(email, ignoreCase = true) }
        for (info in allKeys) {
            val armored = storage.loadPublicKey(info.fingerprint) ?: continue
            try {
                PGPainless.readKeyRing().publicKeyRing(armored)
                return info.fingerprint
            } catch (_: Exception) { continue }
        }
        return null
    }

    fun loadSecretKeyRing(fingerprint: String): org.bouncycastle.openpgp.PGPSecretKeyRing? {
        val armored = storage.loadPrivateKey(fingerprint) ?: return null
        return try {
            PGPainless.readKeyRing().secretKeyRing(armored)
        } catch (_: Exception) { null }
    }

    fun loadPublicKeyRing(fingerprint: String): org.bouncycastle.openpgp.PGPPublicKeyRing? {
        val armored = storage.loadPublicKey(fingerprint) ?: return null
        return try {
            PGPainless.readKeyRing().publicKeyRing(armored)
        } catch (_: Exception) { null }
    }

    private fun keyRingToInfo(
        ring: org.bouncycastle.openpgp.PGPSecretKeyRing,
        isPrivate: Boolean
    ): PgpKeyInfo {
        val info = PGPainless.inspectKeyRing(ring)
        return PgpKeyInfo(
            fingerprint = info.fingerprint.toString(),
            userId = info.primaryUserId ?: "Unknown",
            algorithm = info.algorithm.name,
            creationDate = info.creationDate.time,
            isPrivate = isPrivate,
            isExpired = info.primaryKeyExpirationDate?.let { it.before(Date()) } ?: false
        )
    }

    private fun keyRingToInfo(
        ring: org.bouncycastle.openpgp.PGPPublicKeyRing,
        isPrivate: Boolean
    ): PgpKeyInfo {
        val info = PGPainless.inspectKeyRing(ring)
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
