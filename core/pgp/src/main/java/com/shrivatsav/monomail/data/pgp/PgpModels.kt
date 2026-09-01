package com.shrivatsav.monomail.data.pgp

import com.google.gson.annotations.SerializedName

data class PgpKeyInfo(
    @SerializedName("fingerprint") val fingerprint: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("algorithm") val algorithm: String,
    @SerializedName("creationDate") val creationDate: Long,
    @SerializedName("isPrivate") val isPrivate: Boolean,
    @SerializedName("isExpired") val isExpired: Boolean,
    @SerializedName("isPassphraseProtected") val isPassphraseProtected: Boolean = false
)

data class PgpSignature(
    val isValid: Boolean,
    val signer: String
)

data class PgpDecryptionResult(
    val decryptedBody: String,
    val signatures: List<PgpSignature>? = null,
    val needsPassphrase: Boolean = false,
    val fingerprint: String? = null
)

data class PgpEncryptionResult(
    val encryptedBody: String,
    val contentType: String = "multipart/encrypted; protocol=\"application/pgp-encrypted\""
)
