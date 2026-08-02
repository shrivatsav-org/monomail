package com.shrivatsav.monomail.core.data.licensing

import android.content.Context
import android.util.Log
import com.shrivatsav.monomail.core.data.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play Store licensing gate for the Gmail API provider.
 *
 * Play builds (paid app on Google Play) resolve licensing through the Play
 * Integrity API's `appLicensingVerdict`: the store auto-licenses every device
 * whose account owns the app, and refunds/revocations flip the verdict to
 * UNLICENSED. GitHub builds have no Gmail API at all — [gmailApiAvailable]
 * is false there and the gate is effectively compiled out.
 */
class LicenseManager(context: Context) {

    companion object {
        private const val TAG = "LicenseManager"

        /** Gmail API is a Play-only feature; GitHub builds use IMAP/Outlook. */
        val gmailApiAvailable: Boolean
            get() = !BuildConfig.IS_GITHUB_BUILD
    }

    private val checker = LicenseCheckerHolder.create(context.applicationContext)

    private val _isLicensed = MutableStateFlow(checker.isCachedLicensed())
    val isLicensed: StateFlow<Boolean> = _isLicensed.asStateFlow()

    /** Re-validates the Play license; returns true only for a LICENSED verdict. */
    suspend fun checkLicense(): Boolean {
        if (!gmailApiAvailable) {
            _isLicensed.value = false
            return false
        }
        val licensed = checker.check()
        _isLicensed.value = licensed
        if (!licensed) Log.w(TAG, "Play license check failed — Gmail API disabled")
        return licensed
    }

    /** Clears any cached verdict (e.g. after a refund). */
    fun clearLicense() {
        checker.clearCache()
        _isLicensed.value = false
    }
}
