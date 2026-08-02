package com.shrivatsav.monomail.model

/**
 * Feature flags for gating features during rollout.
 * 
 * Gmail OAuth is now invite-only — new Gmail users must use App Passwords.
 * Existing OAuth users are unaffected.
 */
object FeatureFlags {
    /**
     * When false, the "Sign in with Gmail (OAuth)" button is hidden.
     * Only IMAP/App Password is available for new Gmail sign-ins.
     * Existing OAuth accounts continue working.
     */
    const val GMAIL_OAUTH_ENABLED = false

    /**
     * When true, shows an advanced option to sign in via OAuth for Gmail.
     * This is the invite-only path for users who need OAuth features
     * (e.g., push notifications via Pub/Sub).
     */
    const val SHOW_ADVANCED_OAUTH_OPTION = false

    /**
     * Enable IMAP IDLE for real-time push on Gmail.
     * Requires foreground service.
     */
    const val IMAP_IDLE_ENABLED = false  // Enable when IDLE service is tested

    /**
     * Enable CONDSTORE for efficient change detection.
     * Gmail supports CONDSTORE but not QRESYNC.
     */
    const val CONDSTORE_ENABLED = false  // Enable after connection pool is stable
}
