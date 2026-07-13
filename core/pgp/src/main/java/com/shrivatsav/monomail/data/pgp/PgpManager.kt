package com.shrivatsav.monomail.data.pgp

import android.util.Log
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.api.OpenPGPCertificate
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.encryption_signing.SigningOptions
import org.pgpainless.key.parsing.KeyRingReader
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
            val result = tryDecryptWithKey(emailBody, fp)
            if (result != null) return result
        }

        return null
    }

    private fun tryDecryptWithKey(emailBody: String, fp: String): PgpDecryptionResult? {
        val armoredSecret = storage.loadPrivateKey(fp) ?: run {
            Log.d("PgpManager", "No private key file for $fp, checking storage...")
            return null
        }
        val secretKeyRing = try {
            KeyRingReader().secretKeyRing(armoredSecret)
                ?: throw NullPointerException("readKeyRing returned null")
        } catch (e: Exception) {
            Log.e("PgpManager", "Failed to parse private key ring for $fp", e)
            return null
        }

        return try {
            val protector = getProtector(fp)
            val consumerOptions = ConsumerOptions.get()
                .addDecryptionKey(PGPainless.getInstance().toKey(secretKeyRing), protector)
            val decryptionStream = PGPainless.getInstance().processMessage()
                .onInputStream(ByteArrayInputStream(emailBody.toByteArray()))
                .withOptions(consumerOptions)
            val decrypted = decryptionStream.readBytes().toString(Charsets.UTF_8)
            val metadata = decryptionStream.metadata
            decryptionStream.close()

            val signatures = metadata.verifiedSignatures.map { sig ->
                PgpSignature(isValid = true, signer = sig.signingKey?.toString() ?: "Unknown")
            }
            PgpDecryptionResult(decryptedBody = decrypted, signatures = signatures.ifEmpty { null })
        } catch (e: Exception) {
            Log.e("PgpManager", "Decryption failed for $fp", e)
            val metadata = storage.loadKeyMetadata(fp)
            if (metadata?.isPassphraseProtected == true && storage.loadPassphrase(fp) == null) {
                PgpDecryptionResult(decryptedBody = "", needsPassphrase = true, fingerprint = fp)
            } else {
                null
            }
        }
    }

    fun encryptBody(
        plaintext: String,
        toAddresses: List<String>
    ): PgpEncryptionResult? {
        val recipientRings = toAddresses.mapNotNull { address ->
            val fp = keyManager.getPublicKeyForRecipientAsFingerprint(address) ?: return@mapNotNull null
            val armored = storage.loadPublicKey(fp) ?: return@mapNotNull null
            try {
                KeyRingReader().publicKeyRing(armored)
            } catch (e: Exception) {
                Log.w("PgpManager", "Failed to parse public key ring for recipient $address", e)
                null
            }
        }

        if (recipientRings.isEmpty()) return null

        try {
            val encryptionOptions = EncryptionOptions.get()
            for (ring in recipientRings) {
                encryptionOptions.addRecipient(OpenPGPCertificate(ring))
            }

            val producerOptions = ProducerOptions.encrypt(encryptionOptions)
            val outputStream = ByteArrayOutputStream()

            val encryptionStream = PGPainless.getInstance().generateMessage()
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
            KeyRingReader().secretKeyRing(armoredSecret)!!
        } catch (e: Exception) {
            Log.w("PgpManager", "Failed to parse secret key ring for signing $fingerprint", e)
            return null
        }

        try {
            val protector = getProtector(fingerprint)

            val signingOptions = SigningOptions.get()
                .addInlineSignature(protector, PGPainless.getInstance().toKey(secretKeyRing))

            val producerOptions = ProducerOptions.sign(signingOptions)
            val outputStream = ByteArrayOutputStream()

            val signingStream = PGPainless.getInstance().generateMessage()
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
                KeyRingReader().publicKeyRing(armored)
            } catch (e: Exception) {
                Log.w("PgpManager", "Failed to parse public key ring for $address (encryptAndSign)", e)
                null
            }
        }

        if (recipientRings.isEmpty()) return null

        try {
            val encryptionOptions = EncryptionOptions.get()
            for (ring in recipientRings) {
                encryptionOptions.addRecipient(OpenPGPCertificate(ring))
            }

            val producerOptions: ProducerOptions = if (signingFingerprint != null) {
                val armoredSecret = storage.loadPrivateKey(signingFingerprint) ?: return null
                val secretKeyRing = try {
                    KeyRingReader().secretKeyRing(armoredSecret)!!
                } catch (e: Exception) {
                    Log.w("PgpManager", "Failed to parse secret key ring for signing $signingFingerprint (encryptAndSign)", e)
                    return null
                }

                val protector = getProtector(signingFingerprint)
                val signingOptions = SigningOptions.get()
                    .addInlineSignature(protector, PGPainless.getInstance().toKey(secretKeyRing))

                ProducerOptions.signAndEncrypt(encryptionOptions, signingOptions)
            } else {
                ProducerOptions.encrypt(encryptionOptions)
            }

            val outputStream = ByteArrayOutputStream()
            val encryptionStream = PGPainless.getInstance().generateMessage()
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
