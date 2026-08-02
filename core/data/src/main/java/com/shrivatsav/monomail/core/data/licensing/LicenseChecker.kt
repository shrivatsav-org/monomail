package com.shrivatsav.monomail.core.data.licensing

/**
 * Flavor-specific Play licensing backend. The playstore flavor implements it
 * with the Play Integrity API; the github flavor is a stub (no Gmail API).
 */
interface LicenseChecker {

    /** Last known verdict without hitting the network. */
    fun isCachedLicensed(): Boolean

    /** Runs a licensing check; true only for a LICENSED verdict. */
    suspend fun check(): Boolean

    /** Drops the cached verdict. */
    fun clearCache()
}
