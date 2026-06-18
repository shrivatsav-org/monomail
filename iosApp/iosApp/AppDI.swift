import Foundation
import shared

/// Composition root for the iOS app. Builds the shared `AppModule` once with the
/// iOS platform implementations, then exposes the wired singletons to SwiftUI.
///
/// NOTE: fill in the OAuth client IDs from your Google (iOS native client) and
/// Microsoft (Azure public client) registrations. The redirect/scheme must match
/// what you register there AND the CFBundleURLSchemes entry in Info.plist.
enum AppDI {
    static let module: AppModule = {
        let oauth = OAuthClientConfig(
            gmailClientId: "TODO_GOOGLE_IOS_CLIENT_ID",
            outlookClientId: "TODO_OUTLOOK_PUBLIC_CLIENT_ID",
            redirectUri: "com.shrivatsav.monomail://oauth2redirect",
            callbackScheme: "com.shrivatsav.monomail"
        )
        return AppModule(
            secureStore: IosSecureStore(defaults: .standard, namespace: "secure."),
            keyValueStore: IosKeyValueStore(defaults: .standard),
            driverFactory: IosSqlDriverFactory(),
            browser: OAuthBrowser(),
            oauthConfig: oauth
        )
    }()
}
