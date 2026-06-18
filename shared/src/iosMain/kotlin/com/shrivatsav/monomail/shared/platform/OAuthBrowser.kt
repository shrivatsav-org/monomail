package com.shrivatsav.monomail.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS OAuth redirect via ASWebAuthenticationSession.
 * NOTE: must be invoked on the main thread; the SwiftUI caller drives this from
 * the main actor (SKIE async).
 */
@OptIn(ExperimentalForeignApi::class)
actual class OAuthBrowser {
    private val contextProvider = PresentationContextProvider()

    actual suspend fun authorize(authUrl: String, callbackScheme: String): String =
        suspendCancellableCoroutine { cont ->
            val url = NSURL(string = authUrl)
            if (url == null) {
                cont.resumeWithException(IllegalArgumentException("Invalid auth URL"))
                return@suspendCancellableCoroutine
            }
            val session = ASWebAuthenticationSession(
                uRL = url,
                callbackURLScheme = callbackScheme
            ) { callbackURL, error ->
                when {
                    error != null -> cont.resumeWithException(RuntimeException(error.localizedDescription))
                    callbackURL != null -> cont.resume(callbackURL.absoluteString ?: "")
                    else -> cont.resumeWithException(RuntimeException("No callback URL returned"))
                }
            }
            session.prefersEphemeralWebBrowserSession = false
            session.presentationContextProvider = contextProvider
            cont.invokeOnCancellation { session.cancel() }
            if (!session.start()) {
                cont.resumeWithException(RuntimeException("Failed to start auth session"))
            }
        }
}

@OptIn(ExperimentalForeignApi::class)
private class PresentationContextProvider :
    NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor {
        return UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}
