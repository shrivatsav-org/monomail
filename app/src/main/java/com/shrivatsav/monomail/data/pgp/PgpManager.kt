package com.shrivatsav.monomail.data.pgp

import android.util.Log
import org.bouncycastle.openpgp.PGPException
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.encryption_signing.SigningOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
import org.pgpainless.util.Passphrase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PgpManager @Inject constructor(
    private val keyManager: PgpKeyManager,
    private val storage: PgpKeyStorage
) {
    fun isPgpMessage(body: String): Boolean {
        return body.startsWith("-----BEGIN PGP MESSAGE-----") ||
                body.contains("multipart/encrypted;") ||
                body.contains("multipart/encrypted\r\n") ||
                body.contains("application/pgp-encrypted")
    }

    private fun getProtector(
        secretKeyRing: org.bouncycastle.openpgp.PGPSecretKeyRing,
        fingerprint: String
    ): SecretKeyRingProtector {
        val passphrase = storage.loadPassphrase(fingerprint)
        if (passphrase != null) {
            Log.d("PgpManager", "Using passphrase protector for key $fingerprint")
            return SecretKeyRingProtector.unlockAnyKeyWith(
                Passphrase.fromPassword(passphrase)
            )
        }
        return SecretKeyRingProtector.unprotectedKeys()
    }

    fun decryptBody(emailBody: String, fingerprintHint: String? = null): PgpDecryptionResult? {
        if (!isPgpMessage(emailBody)) return null

        val fingerprints = if (fingerprintHint != null) {
            listOf(fingerprintHint)
        } else {
            keyManager.listKeys()
                .filter { storage.privateKeyExists(it.fingerprint) }
                .map { it.fingerprint }
        }

        for (fp in fingerprints) {
            try {
                val armoredSecret = storage.loadPrivateKey(fp)
                if (armoredSecret == null) {
                    Log.d("PgpManager", "No private key file for $fp, checking storage...")
                    continue
                }
                val secretKeyRing = try {
                    PGPainless.readKeyRing().secretKeyRing(armoredSecret)
                        ?: throw NullPointerException("readKeyRing returned null")
                } catch (e: Exception) {
                    Log.e("PgpManager", "Failed to parse private key ring for $fp", e)
                    continue
                }

                val protector = getProtector(secretKeyRing, fp)

                val consumerOptions = ConsumerOptions.get()
                    .addDecryptionKey(secretKeyRing, protector)

                val decryptionStream = PGPainless.decryptAndOrVerify()
                    .onInputStream(ByteArrayInputStream(emailBody.toByteArray()))
                    .withOptions(consumerOptions)

                val decryptedBytes = decryptionStream.readBytes()
                decryptionStream.close()

                val decrypted = decryptedBytes.toString(Charsets.UTF_8)

                val metadata = decryptionStream.metadata
                val signatures = mutableListOf<PgpSignature>()

                for (sig in metadata.verifiedSignatures) {
                    signatures.add(
                        PgpSignature(
                            isValid = true,
                            signer = sig.signingKey?.toString() ?: "Unknown"
                        )
                    )
                }

                return PgpDecryptionResult(
                    decryptedBody = decrypted,
                    signatures = signatures.ifEmpty { null }
                )
            } catch (e: Exception) {
                Log.e("PgpManager", "Decryption failed for $fp", e)
                // If the key is passphrase-protected but no passphrase is stored,
                // signal the caller so the UI can prompt the user
                val metadata = storage.loadKeyMetadata(fp)
                if (metadata?.isPassphraseProtected == true && storage.loadPassphrase(fp) == null) {
                    return PgpDecryptionResult(
                        decryptedBody = "",
                        needsPassphrase = true,
                        fingerprint = fp
                    )
                }
                continue
            }
        }

        return null
    }

    fun encryptBody(
        plaintext: String,
        toAddresses: List<String>
    ): PgpEncryptionResult? {
        val recipientRings = toAddresses.mapNotNull { address ->
            val fp = keyManager.getPublicKeyForRecipientAsFingerprint(address) ?: return@mapNotNull null
            val armored = storage.loadPublicKey(fp) ?: return@mapNotNull null
            try {
                PGPainless.readKeyRing().publicKeyRing(armored)
            } catch (e: Exception) {
                Log.w("PgpManager", "Failed to parse public key ring for recipient $address", e)
                null
            }
        }

        if (recipientRings.isEmpty()) return null

        try {
            val encryptionOptions = EncryptionOptions.get()
            for (ring in recipientRings) {
                encryptionOptions.addRecipient(ring)
            }

            val producerOptions = ProducerOptions.encrypt(encryptionOptions)
            val outputStream = ByteArrayOutputStream()

            val encryptionStream = PGPainless.encryptAndOrSign()
                .onOutputStream(outputStream)
                .withOptions(producerOptions)

            encryptionStream.write(plaintext.toByteArray())
            encryptionStream.close()

            val encrypted = outputStream.toString(Charsets.UTF_8.name())
            return PgpEncryptionResult(encryptedBody = encrypted)
        } catch (e: Exception) {
            Log.e("PgpManager", "Encryption failed", e)
            return null
        }
    }

    fun signBody(body: String, fingerprint: String): String? {
        val armoredSecret = storage.loadPrivateKey(fingerprint) ?: return null
        val secretKeyRing = try {
            PGPainless.readKeyRing().secretKeyRing(armoredSecret)!!
        } catch (e: Exception) {
            Log.w("PgpManager", "Failed to parse secret key ring for signing $fingerprint", e)
            return null
        }

        try {
            val protector = getProtector(secretKeyRing, fingerprint)

            val signingOptions = SigningOptions.get()
                .addInlineSignature(protector, secretKeyRing)

            val producerOptions = ProducerOptions.sign(signingOptions)
            val outputStream = ByteArrayOutputStream()

            val signingStream = PGPainless.encryptAndOrSign()
                .onOutputStream(outputStream)
                .withOptions(producerOptions)

            signingStream.write(body.toByteArray())
            signingStream.close()

            return outputStream.toString(Charsets.UTF_8.name())
        } catch (e: Exception) {
            Log.w("PgpManager", "Signing failed for $fingerprint", e)
            return null
        }
    }

    fun encryptAndSignBody(
        plaintext: String,
        toAddresses: List<String>,
        signingFingerprint: String? = null
    ): PgpEncryptionResult? {
        val recipientRings = toAddresses.mapNotNull { address ->
            val fp = keyManager.getPublicKeyForRecipientAsFingerprint(address) ?: return@mapNotNull null
            val armored = storage.loadPublicKey(fp) ?: return@mapNotNull null
            try {
                PGPainless.readKeyRing().publicKeyRing(armored)
            } catch (e: Exception) {
                Log.w("PgpManager", "Failed to parse public key ring for $address (encryptAndSign)", e)
                null
            }
        }

        if (recipientRings.isEmpty()) return null

        try {
            val encryptionOptions = EncryptionOptions.get()
            for (ring in recipientRings) {
                encryptionOptions.addRecipient(ring)
            }

            val producerOptions: ProducerOptions = if (signingFingerprint != null) {
                val armoredSecret = storage.loadPrivateKey(signingFingerprint) ?: return null
                val secretKeyRing = try {
                    PGPainless.readKeyRing().secretKeyRing(armoredSecret)!!
                } catch (e: Exception) {
                    Log.w("PgpManager", "Failed to parse secret key ring for signing $signingFingerprint (encryptAndSign)", e)
                    return null
                }

                val protector = getProtector(secretKeyRing, signingFingerprint)
                val signingOptions = SigningOptions.get()
                    .addInlineSignature(protector, secretKeyRing)

                ProducerOptions.signAndEncrypt(encryptionOptions, signingOptions)
            } else {
                ProducerOptions.encrypt(encryptionOptions)
            }

            val outputStream = ByteArrayOutputStream()
            val encryptionStream = PGPainless.encryptAndOrSign()
                .onOutputStream(outputStream)
                .withOptions(producerOptions)

            encryptionStream.write(plaintext.toByteArray())
            encryptionStream.close()

            val encrypted = outputStream.toString(Charsets.UTF_8.name())
            return PgpEncryptionResult(encryptedBody = encrypted)
        } catch (e: Exception) {
            Log.e("PgpManager", "encryptAndSignBody failed", e)
            return null
        }
    }

    fun getAvailableEncryptionKeys(): List<PgpKeyInfo> {
        return keyManager.listKeys().filter { storage.publicKeyExists(it.fingerprint) }
    }

    fun getAvailableSigningKeys(): List<PgpKeyInfo> {
        return keyManager.listKeys().filter { storage.privateKeyExists(it.fingerprint) }
    }
}
