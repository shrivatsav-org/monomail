package com.shrivatsav.monomail.shared.auth

import com.shrivatsav.monomail.shared.util.Base64Util
import com.shrivatsav.monomail.shared.util.Sha256
import kotlin.random.Random

/** A PKCE code_verifier / code_challenge (S256) pair plus an anti-CSRF state. */
data class PkcePair(
    val verifier: String,
    val challenge: String,
    val state: String
) {
    val challengeMethod: String = "S256"
}

object Pkce {
    fun generate(): PkcePair {
        val verifier = Base64Util.encodeUrl(Random.nextBytes(32))
        val challenge = Base64Util.encodeUrl(Sha256.hash(verifier.encodeToByteArray()))
        val state = Base64Util.encodeUrl(Random.nextBytes(16))
        return PkcePair(verifier = verifier, challenge = challenge, state = state)
    }
}
