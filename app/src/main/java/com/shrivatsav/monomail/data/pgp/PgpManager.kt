package com.shrivatsav.monomail.data.pgp

import org.bouncycastle.openpgp.PGPException
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import org.pgpainless.encryption_signing.SigningOptions
import org.pgpainless.key.protection.SecretKeyRingProtector
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
        return body.contains("-----BEGIN PGP MESSAGE-----") ||
                body.contains("multipart/encrypted") ||
                body.contains("application/pgp-encrypted") ||
                body.contains("-----BEGIN PGP SIGNED MESSAGE-----")
    }

    fun decryptBody(emailBody: String, fingerprintHint: String? = null): PgpDecryptionResult? {
        if (!isPgpMessage(emailBody)) return null

        val fingerprints = if (fingerprintHint != null) {
            listOf(fingerprintHint)
        } else {
            keyManager.listKeys()
                .filter { it.isPrivate }
                .map { it.fingerprint }
        }

        for (fp in fingerprints) {
            try {
                val armoredSecret = storage.loadPrivateKey(fp) ?: continue
                val secretKeyRing = try {
                    PGPainless.readKeyRing().secretKeyRing(armoredSecret)!!
                } catch (_: Exception) { continue }

                val protector = SecretKeyRingProtector.unprotectedKeys()

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
            } catch (_: Exception) {
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
            } catch (_: Exception) { null }
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
            return null
        }
    }

    fun signBody(body: String, fingerprint: String): String? {
        val armoredSecret = storage.loadPrivateKey(fingerprint) ?: return null
        val secretKeyRing = try {
            PGPainless.readKeyRing().secretKeyRing(armoredSecret)!!
        } catch (_: Exception) { return null }

        try {
            val protector = SecretKeyRingProtector.unprotectedKeys()

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
            return null
        }
    }

    fun getAvailableEncryptionKeys(): List<PgpKeyInfo> {
        return keyManager.listKeys().filter { !it.isPrivate }
    }

    fun getAvailableSigningKeys(): List<PgpKeyInfo> {
        return keyManager.listKeys().filter { it.isPrivate }
    }
}
