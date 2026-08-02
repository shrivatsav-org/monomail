package com.shrivatsav.monomail.core.data.licensing

import android.content.Context

object LicenseCheckerHolder {

    /** GitHub builds have no Gmail API — the gate is always closed. */
    fun create(context: Context): LicenseChecker = object : LicenseChecker {
        override fun isCachedLicensed(): Boolean = false
        override suspend fun check(): Boolean = false
        override fun clearCache() {}
    }
}
