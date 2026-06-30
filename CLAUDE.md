# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build debug APK (github flavor — no Google Client ID needed)
./gradlew assembleGithubDebug

# Build playstore debug (requires secrets.properties with GOOGLE_CLIENT_ID)
./gradlew assemblePlaystoreDebug

# Build release
./gradlew assembleGithubRelease

# Install on device
./gradlew installGithubDebug

# Run tests
./gradlew test                               # unit tests
./gradlew connectedAndroidTest               # instrumented tests

# Run a single unit test
./gradlew app:testDebugUnitTest --tests "com.shrivatsav.monomail.ExampleTest"

# Run a single instrumented test
./gradlew app:connectedDebugAndroidTest --tests "com.shrivatsav.monomail.ExampleInstrumentedTest"
```

**Prerequisites:** JDK 17+, Android Studio, Android SDK 26+. For the `playstore` flavor, create `secrets.properties` in the project root with `GOOGLE_CLIENT_ID=your_web_client_id_here`.

## Project Architecture

### Single-module Android app using Jetpack Compose

There is one module (`:app`) with two product flavors: `github` (no bundled API keys) and `playstore` (bundled API keys). All source is under `app/src/main/java/com/shrivatsav/monomail/`.

### Package structure

#### `auth/` — Authentication & account management
- **`AuthManager`** — top-level auth orchestrator: Google sign-in via Credential Manager, token refresh, session restore (two-phase: `restoreSessionQuick` for cold start, then background `restoreSession`), account switching, sign-out.
- **`AccountManager`** — persists accounts as encrypted JSON in DataStore (encrypted via `SecurityUtil` with AES-GCM/Android KeyStore). Handles multi-account storage, active account tracking, last-known-email-id for notification dedup, last-active-time for adaptive sync.
- **`MicrosoftAuthManager`** — MSAL-based Outlook authentication.
- **`UserProfile`** — data class with id, email, displayName, photoUrl, accessToken, provider ("gmail"|"outlook"|"imap"), refreshToken.

#### `data/` — Data layer (offline-first)

- **`data/provider/`** — Provider abstraction over email backends
  - **`EmailProvider`** (interface) — `listThreads`, `getThread`, `archiveThread`, `toggleStar`, `sendEmail`, etc.
  - **`GmailProvider`** — implements via Gmail API (Retrofit)
  - **`OutlookProvider`** — implements via Microsoft Graph API
  - **`ImapProvider`** — implements via Jakarta Mail (Eclipse Angus) for IMAP/SMTP
  - **`ProviderModels.kt`** — `ProviderThread`, `ProviderMessage`, `ProviderThreadListResult`

- **`data/local/`** — Room database with SQLCipher encryption
  - **`AppDatabase`** — 4 entities: `ThreadEntity`, `EmailEntity`, `ScheduledMessageEntity`, `PendingActionEntity`. DB version 10 with explicit migrations for all upgrades.
  - **`Entities.kt`** — Room entities with `toDomainModel()` / `toEntity()` extension functions bridging Room ↔ domain models.
  - **DAOs** — `ThreadDao`, `EmailDao`, `ScheduledMessageDao`, `PendingActionDao`

- **`data/remote/`** — Retrofit API interfaces (`GmailApi`, `OutlookApi`) and `RetrofitClient` (provides OkHttp with interceptor for token injection and auto-refresh on 401).

- **`data/repository/`** — **`EmailRepository`** — the central hub for all data operations. Coordinates between providers, local database, pending actions, and scheduled sends. All write operations first update local state immediately (optimistic), then enqueue a `PendingActionEntity` for background sync.

- **`data/settings/`** — **`SettingsDataStore`** — all user preferences stored in DataStore Preferences, exposed as a pre-heated `StateFlow<AppSettings>` (eagerly started during singleton construction so the first frame reads cached values without defaults flashing).

- **`data/worker/`** — **`ActionQueueManager`** — background singleton that processes `PendingActionEntity` rows (star, archive, delete, mark-read) sequentially against the server. Runs in its own coroutine scope started at app launch.

#### `di/` — Hilt modules
- **`AppModule`** — singleton bindings for AccountManager, AuthManager, SettingsDataStore, ContactSuggestionProvider, email event flows, and the EmailProvider factory (which caches providers per account and handles credential refresh).
- **`DatabaseModule`** — Room database and DAO bindings.

#### `push/` — Push notifications
- **`PushNotificationManager`** — handles notification setup, registration/unregistration with push backend, and notification display.
- **`PushBackendClient`** — HTTP client for the push relay backend (Workers).

#### `security/`
- **`SecurityUtil`** — AES-GCM encryption via Android KeyStore, EncryptedSharedPreferences for DB passphrase and Google Client ID storage, DB passphrase generation.

#### `ui/` — Jetpack Compose UI

- **`ui/navigation/NavGraph.kt`** — defines all routes as `Screen` sealed class. Two-phase auth: `restoreSessionQuick` for instant splash dismissal, then background `restoreSession`. Start destination is Inbox (authenticated), Onboarding (first-launch), or SignIn.
- **`ui/theme/`** — monochrome color scheme (black/white/grey), Material 3 Expressive with `MotionScheme.expressive()`. Extended colors via `MonoMailExtendedColors` (onSurfaceMuted). Font scaling done by wrapping `LocalDensity` at the top level.

- **Screen packages under `ui/screens/`:**
  - **`inbox/`** — InboxScreen, InboxViewModel, EmailItem, SwipeableEmailItem, BottomDockBar, BulkActionBar, InboxSearchBar, AvatarCircle, ProfileCard, SwitchAccountCard, ModalOverlay, DockTab
  - **`detail/`** — EmailDetailScreen, EmailDetailViewModel (HTML WebView rendering, conversation/chain view toggle, quoted text collapse)
  - **`compose/`** — ComposeScreen, ComposeViewModel (new/reply/forward/edit-scheduled, CC/BCC, file attachments, templates, schedule send)
  - **`auth/`** — OnboardingScreen, SignInScreen, SignInViewModel, ImapSetupScreen, ImapSetupViewModel
  - **`settings/`** — SettingsScreen, SettingsViewModel (all preferences, dock bar editor, template management, licenses)
  - **`scheduled/`** — ScheduledMessagesScreen, ScheduledMessagesViewModel

#### `worker/` — WorkManager workers
- **`EmailSyncWorker`** — periodic background sync with adaptive interval (~2 min when recently active, 15 min otherwise). Creates per-account notification channels. Supports inline reply, archive, and snooze actions from notifications.
- **`ScheduledSendWorker`** — fires at the scheduled time to send a message.
- **`SnoozeWorker`** — moves snoozed threads back to inbox.
- **`GraphSubscriptionRenewalWorker`** — daily renewal of Outlook webhook subscriptions.
- **`NotificationActionReceiver`** — BroadcastReceiver for notification action buttons (reply, archive, snooze).

### Key Architecture Patterns

1. **Offline-first with optimistic updates:** All user actions (star, archive, delete, mark-read) update the local Room database immediately, then enqueue a `PendingActionEntity`. `ActionQueueManager` processes these asynchronously against the server, retrying up to 5 times before marking as FAILED.

2. **EmailProvider abstraction:** Each backend (Gmail, Outlook, IMAP) implements the same `EmailProvider` interface. The factory in `AppModule` creates and caches providers per account. All provider selection is driven by the `UserProfile.provider` string field.

3. **Two-phase session restore:** On cold start, `restoreSessionQuick()` reads cached auth state synchronously so the UI frame renders instantly. Then `restoreSession()` refreshes tokens and registers for push in the background.

4. **Two display modes:** "Conversation View" (collapsible message list, only latest expanded) vs "Message Chain" (all emails inline). Controlled by `organizeByThread` setting. The inbox also has a mode switch between thread-based (default) and email-based (when `organizeByThread=false`, emails are grouped client-side by threadId).

5. **Undo pattern:** Actions (archive, delete, send, snooze) hide the item immediately and show an undo toast for 4 seconds. If the user doesn't undo, the actual database/API operation executes. Uses `pendingActionJobs` map of coroutine Jobs keyed by threadId for cancellation.

6. **Coil for image loading:** Domain favicons on sender avatars, photo URLs on profile cards.

7. **Every screen uses its own Hilt ViewModel** injected via `hiltViewModel()`. The `NavGraph` passes callbacks (not the ViewModel) as navigation lambdas.
