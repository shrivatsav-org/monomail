# MonoMail iOS

SwiftUI app consuming the shared KMP module (`:shared`) for all business logic.
Auth, networking, persistence, and view models live in Kotlin; only UI + the
OAuth browser redirect are native.

> **Status:** scaffolding. The shared Kotlin compiles clean for all iOS targets.
> The framework link, XCFramework export, and this Swift app have **not** been
> built yet (no Xcode in the authoring environment). Expect to tune the Swift
> against the generated framework header the first time you build — see Caveats.

## Prerequisites

- Xcode 15+ and command-line tools (`xcode-select --install`)
- JDK 17+ (for Gradle)
- [XcodeGen](https://github.com/yonyz/XcodeGen) (`brew install xcodegen`)
- A free Apple ID for signing (personal sideload; no paid account needed)

## 1. Register OAuth clients (one-time)

PKCE replaces MSAL + Google Credentials Manager, so you need **public/native**
OAuth clients with a custom-scheme redirect.

- **Google** — create an *iOS* OAuth client (and later an *Android* one) in Google
  Cloud Console. Bundle ID: `com.shrivatsav.monomail`. The old `GOOGLE_CLIENT_ID`
  (a Web client) does **not** work for PKCE.
- **Microsoft** — register a *public client* app in Azure AD (Entra). Add the
  redirect URI `com.shrivatsav.monomail://oauth2redirect`. Grant Graph scopes
  `Mail.ReadWrite`, `Mail.Send`, `offline_access`.

Redirect URI for both: `com.shrivatsav.monomail://oauth2redirect`
(scheme = `com.shrivatsav.monomail`, already in `Info.plist`).

Put the client IDs into `iosApp/AppDI.swift` (`OAuthClientConfig`).

## 2. Build the shared framework

```bash
# from repo root
./gradlew :shared:assembleSharedDebugXCFramework
# output: shared/build/XCFrameworks/debug/shared.xcframework
```

(Compile-only sanity check, no Xcode link: `./gradlew :shared:compileKotlinIosSimulatorArm64`.)

## 3. Generate and open the Xcode project

```bash
cd iosApp
xcodegen generate          # reads project.yml -> MonoMail.xcodeproj
open MonoMail.xcodeproj
```

Set your signing team (Signing & Capabilities → your free Apple ID), pick a
simulator or your device, Run.

## 4. SKIE (Swift-friendly bindings)

The Swift code assumes [SKIE](https://skie.touchlab.co) is enabled (StateFlow →
`AsyncSequence`, suspend → `async`, sealed → Swift enum). To turn it on:

1. Pin a `skie` version compatible with this project's Kotlin in
   `gradle/libs.versions.toml` (the catalog has a placeholder).
2. Uncomment `alias(libs.plugins.skie)` in `shared/build.gradle.kts`.
3. Rebuild the XCFramework.

Without SKIE: the flow observation in `FlowObserver.swift` and the sealed-class
casts in the views must be rewritten against the raw Kotlin/Native ObjC interface
(class names get a `Shared` prefix; flows need manual `collect`).

## Caveats / TODO

- **7-day resign:** free provisioning certs expire weekly — rebuild from Xcode to
  keep the app launching. Not a permanent install like an Android `.apk`.
- **Swift ↔ Kotlin names:** the sealed-state casts (`InboxStateSuccess`, etc.) and
  default-arg initializers are best-effort; adjust to the generated header.
- **Security (interim):** `IosSecureStore` currently uses `NSUserDefaults`, not the
  Keychain — see `TODO(security)` in the shared module. Replace before real use.
- **Data Protection:** `MonoMail.entitlements` requests `NSFileProtectionComplete`.
  If free provisioning rejects it, drop it (passcode devices default to Complete).
- **Screens:** only sign-in → inbox → detail are scaffolded. Compose + settings
  view models exist in shared; their SwiftUI screens are still to do.
