# MonoMail

A monochrome email client for Android, built with Jetpack Compose and Material 3 Expressive. No colour accents, no noise, just email.

[Website](https://monomail.millosaurs.me) · [Download APK](https://github.com/shrivatsav-0/monomail/releases/latest) · [Discord](https://discord.gg/monomail)

> **Gmail API Warning:** This app uses Gmail API OAuth verification which is limited to **100 test users**. The app is not published on the Play Store (cannot afford the developer account fee), so Google will show **"This app is blocked"** for any user beyond the first 100. If you encounter this, the test user quota has been exhausted. You can either run the app from source with your own Google Cloud project credentials, or use an Outlook account instead (no such limit).

---

## Overview

MonoMail is an open-source Android email client that connects to **Gmail**, **Microsoft Outlook**, and **IMAP/SMTP** providers via OAuth and prioritises a distraction-free reading experience. The design system is intentionally monochrome — every screen uses only black, white, and greyscale, built on top of Material 3 Expressive.

The architecture is offline-first: all reads and writes go through a local Room database encrypted with SQLCipher, and background sync via WorkManager + a persistent action queue keeps the remote state consistent without blocking the UI.

---

## Features

### Inbox
- Configurable dock bar: primary tabs in a pill + expandable flyout for remaining tabs. Customize in a dedicated Navigation settings sub-screen (reorder, add up to 4, remove). Default: Inbox, Sent, Archived.
- Folder tabs: Inbox, Sent, Archived, Starred, Trash, Snoozed, Spam, and Unified Inbox (when enabled). Tabs use animated transitions for active state (background, icon tint, label width).
- Snooze: long-press a thread to snooze (1hr, tonight, tomorrow, weekend, next week). Thread returns when time expires. Snoozed tab lists all snoozed threads.
- Spam folder: dedicated tab with report-not-spam and empty-spam actions. Pull-to-refresh fetches from server.
- Pull-to-refresh (Material 3 `PullToRefreshBox`) with embedded progress indicator in the search bar.
- Infinite scroll with pagination for large mailboxes.
- Date headers ("Today", "Yesterday", "Earlier") to organise the list chronologically.
- Smart grouping: auto-collapses threads from the same sender (threshold: 3 emails within 24 hours or 3 days) into expandable group headers showing sender name, count, and unread badge.
- Swipe-to-dismiss gestures on each thread: left and right swipe actions are independently configurable (archive, star, delete, mark read/unread), with colour-coded backgrounds. Uses `SwipeToDismissBox`.
- Long-press contextual overlay with blurred background and four action buttons: star/unstar, archive/unarchive (context-aware), mark read/unread, and delete/restore (context-aware).
- Undo toast with a 4-second window for archive, delete, snooze, and send actions; dismiss to execute immediately.
- **Bulk multi-select mode:** long-press any sender avatar to enter bulk mode with animated checkbox transitions, range select (tap body selects a range from the last toggled item), and select all/deselect all toggle — all with smooth animations. Floating pill-shaped bulk action bar with Archive, Delete, Mark Read, Mark Unread, Star.
- Mark all as read button in the search bar.
- Calendar badge in the search bar showing pending scheduled messages count.
- Sender avatar with domain favicon (loaded via Coil with initial fallback).
- Unread indicator: blue dot on avatar, bold sender/subject text, background tint.
- Message count badge next to sender name for threads with multiple messages.
- Relative timestamps: time (today), day name (this week), date (this year), full date (older).
- Font scaling (5 levels) and compact list mode.
- **Scroll position retention:** each tab's scroll position (index + offset) is saved and restored on tab switch and back navigation.
- Lifecycle-aware foreground polling: syncs every ~2 minutes when app is visible, backs off to configured interval when backgrounded.
- **Performance warning:** dialog shown when adding a 4th+ account advising potential performance impact.

### Conversation and Chain Views
- Toggle in Settings between Conversation View (default) and Message Chain view.
- Conversation View: collapsible message list. Only the latest message is expanded; older messages show a snippet and expand on tap with animated transitions. Subject and message count displayed at the top.
- Message Chain View: all messages expanded inline as a continuous scrollable list with full sender information for each message.
- Unread indicators in chain view: blue dot on avatar, bold sender name, blue bullet on date.
- Message count badge next to sender name in chain view.
- Thread connecting lines, relative timestamps, and alternating background tints for visual separation.
- Collapsible CC/BCC recipients: hidden by default, tap the "To:" line to expand with animated visibility.

### Search
- Local client-side search filters threads by subject, sender name, or snippet in real time.
- Server-side search triggers the Gmail or Outlook API, with paginated results and a "Search server for older emails" prompt when no local results are found.

### Multi-Account and Unified Inbox
- Support for up to 10 Gmail, Microsoft Outlook, and IMAP accounts simultaneously.
- Unified Inbox toggle in the profile card to see all accounts in a single combined tab.
- Quick account switching: horizontal swipe gesture on the profile avatar, or via the account switch modal.
- Profile card with avatar, display name, email, linked accounts count, and quick links to Settings and sign out.

### Compose
- New email, reply, and forward modes with pre-filled recipient and subject fields.
- CC and BCC fields with expand/collapse animation.
- Contact auto-suggestions indexed from threads and messages (debounced, max 5 results, case-insensitive matching), displayed in a card with avatar initials.
- File attachments via system file picker (any MIME type), displayed as preview cards with thumbnails for images and icon with filename for other files. Removable via close button.
- Attachments are base64-encoded and sent as multipart MIME.
- Validation: recipient required, body cannot be empty.
- Confirmation-before-send option in Settings (Compose sub-screen).
- Default reply mode (Reply / Reply All) configurable in Settings (Compose sub-screen).
- **Schedule send:** pick a date & time to send later; attachments are cached and restored at send time. View/edit/cancel scheduled messages from the calendar icon in the search bar.
- **Undo send:** immediate send with a "Undo for Ns" live countdown in the search bar. Configurable undo window (5–30s).
- **Email templates:** save frequently-used bodies as templates. Apply from a bottom sheet in compose; manage in Settings (Compose sub-screen). Empty state with illustration when no templates exist.
- **Send-as aliases:** pick which email address to send from via a dropdown in the From field when the account has multiple send-as addresses configured (Gmail/Outlook).
- **PGP encryption & signing:** lock (encrypt) and pen (sign) toggles in the compose toolbar when PGP keys are available. If encryption is enabled, the email body is encrypted for all recipients using OpenPGP. Keys can be passphrase-protected.
- **Formatting toolbar:** Bold, Italic, Underline, Bullet list, Numbered list, Quote buttons with active state tracking (highlighted when format is applied) via `document.execCommand()` and `queryCommandState()`.
- **Floating dock bottom bar:** pill-shaped elevated `Surface` with attach, schedule, and send buttons. Attach on the left, schedule + send `FilledTonalButton` on the right. Loading spinner shown during send.
- **Animated "Sent!" overlay:** checkmark icon with spring scale/fade animation shown on successful send, auto-dismisses after 1.5s.
- **WebView contenteditable editor** with HTML body editing, JavaScript bridge for body changes and format state, proper disposal to avoid memory leaks.

### Settings
Accessible from the profile card. Settings uses a **hub-and-spoke** architecture: a hub screen shows category cards, each navigating to a dedicated sub-screen with animated slide transitions (drill-in: slide right + fade, pop-out: slide left + fade). The hub screen features a "Made with ❤️" peak-end footer. All settings are persisted via DataStore Preferences.

**Category Cards (Hub):**
- Appearance, Inbox, Compose, Navigation, Notifications, and About.
- PGP Encryption card and Support section (Ko-fi, UPI, GitHub star, Discord, Share, Report Issue, Crypto donate) also on the hub. Support buttons use spring press-scale animation.

**Appearance sub-screen:**
- Theme: System, Light, or Dark with animated spring segmented control (selection bounces to 1.04x scale).
- Font Size: Extra Small, Small, Default, Large, Extra Large (0.7x to 1.3x scaling) with live preview.
- Show Dividers: toggle lines between email items.
- Compact List: reduce spacing in the email list.
- Show Snippet Preview: display preview text below sender in the inbox.
- Load Remote Images: block external images in email bodies (with per-email override).
- Render Markdown: render plain-text emails as formatted Markdown.
- Inline Attachments: show attachment cards within the email body, or as a collapsible summary above it.
- System Font: use device system font instead of Google Sans.
- Email Colors: AUTO (adapt to theme), FORCE_DARK, FORCE_LIGHT, or ORIGINAL (sender's colors on light card) — controls how email HTML bodies are rendered in the detail WebView.
- **Developer sub-section:** Developer Mode toggle to enable share options for raw HTML/MD/plain text email body.

**Inbox sub-screen:**
- Conversation View / Message Chain: toggle between collapsible threads and expanded chain view in email detail.
- Smart Grouping: auto-group threads by sender in the inbox, with nested animated visibility for "Group Recent Only" sub-setting.
- Group Recent Only: restrict smart grouping to the last 24 hours (otherwise 3 days). Enabled by default.
- Swipe Left / Swipe Right: independently configure gesture actions (Archive, Star, Delete, Mark Read/Unread) via bottom sheet pickers.

**Compose sub-screen:**
- Default Reply: Reply or Reply All.
- Confirm Before Sending: show a confirmation dialog before sending each email.
- Undo Send: toggle + animated expand/collapse for window duration picker (5s, 10s, 20s, 30s).
- Templates: add, edit, and delete email templates with empty state illustration.

**Navigation sub-screen:**
- Navigation Size: slider from 0.6x to 1.4x to scale the dock bar and action button.
- Dock Bar Editor: reorder primary tabs (up/down), add tabs from the available list (max 4), remove tabs (min 1). UNIFIED tab auto-hides when Unified Inbox is disabled.

**Notifications sub-screen:**
- Email Notifications: master toggle for notification alerts.
- Sync Frequency: 15 minutes, 30 minutes, 1 hour, or Manual (polling only; no push).
- Per-account notification channels with configurable sound/vibration.

**About sub-screen:**
- Version (1.7.8), Build Info (Product Flavor + Build Type), Push Status (FCM detected via reflection).
- Check for Updates: queries the GitHub releases API and shows status (Up-to-Date / Update Available / Error). Tapping opens the download page.
- Privacy Policy, Terms of Service, Open Source Licenses, License (GPL v3.0).
- PGP Keys: navigate to manage OpenPGP keys.

### Account Management
- Google Sign-In via Android Credential Manager and Google Identity Services.
- Microsoft Sign-In via MSAL (Microsoft Authentication Library) with silent token refresh retry on failure.
- IMAP/SMTP setup with provider presets (Gmail, Outlook, Yahoo, Zoho, Custom) and connection testing.
- Account credentials stored encrypted with AES-GCM via Android KeyStore and EncryptedSharedPreferences.
- Session restoration on app launch (two-phase: quick cached read, then background token refresh).
- Provider-specific sign-out (clears Google Credential Manager or MSAL session independently).
- Add account dialog with Google, Microsoft, and IMAP provider selection.

### Notifications
- Adaptive background sync via WorkManager: polls every ~2 minutes when app is recently active, backs off to 15 minutes when idle.
- New email notification per account with sender name, subject, and system email icon.
- Android 13+ respects POST_NOTIFICATIONS permission.
- Per-account notification channels with configurable sound/vibration.
- **Inline reply** from the notification shade via RemoteInput.
- **Archive + undo** from the notification shade: archive action posts a follow-up undo notification.
- Timestamp-based detection to notify on replies within existing threads.
- Parallelised sync worker: syncing for multiple accounts runs concurrently (fixed in SHR-130).
- **Stale resource handling:** HTTP 404/410 responses on thread/message fetch trigger local cleanup of stale data, with token refresh retry and exponential backoff.

### Email Detail
- HTML body rendering via WebView with JavaScript disabled.
- Collapsible quoted text: hidden by default with a "Show quoted text" toggle and animated reveal — keeps the view clean while preserving full context on demand.
- Custom CSS injection for consistent typography — uses `color-scheme: light dark` for browser-native dark mode adaptation with media query overrides for inline backgrounds. Luminance-based dark mode detection for accurate theme matching.
- **Algorithmic dark mode:** uses `WebSettingsCompat.setAlgorithmicDarkeningAllowed` (AndroidX WebKit) for native WebView darkening, replacing CSS-based monomail-dark injection. Per-email Email Colors setting (AUTO/FORCE_DARK/FORCE_LIGHT/ORIGINAL) controls rendering.
- **Responsive email detection:** emails with responsive signals (viewport meta width or `@media` width overrides) skip zoom and render at native scale for correct layout.
- **Email body normalization:** `normalizeEmailBody` extracts provider-wrapped body text (handles JSON arrays, nested `body`/`content` keys), detects HTML-like content, and normalizes encoding.
- Snippet previews strip quoted text ("On ... wrote:" patterns, `>` lines, and blockquote HTML) for cleaner inbox previews.
- CC and BCC recipients displayed in a styled container in the expanded message section, hidden by default until the user expands the message.
- Conversation view headers show sender avatar, name, From/To email addresses, and relative timestamps.
- Inline image attachments decoded and displayed as previews (max 280dp height) with loading spinner; click to open in system app.
- File attachments displayed in a responsive grid (columns adapt to screen width: 2 on phones, 3 on tablets, 4 on large tablets) with file extension badge, name, and size; download and open via FileProvider.
- **PGP decryption:** detected and decrypted automatically with a green lock badge + signature verification status; undecrypted blobs show a "PGP Encrypted" placeholder. Passphrase-protected keys are handled transparently.
- **Markdown rendering:** plain-text emails rendered as formatted Markdown when enabled in Settings (headers, bold, italic, code blocks, links, lists).
- **Remote image blocking:** external images blocked by default via Content Security Policy, with a "Show" banner for per-email override.
- **Content Security Policy:** `default-src 'none'` with whitelisted `img-src http: https: data:` and `style-src 'unsafe-inline'` for defence-in-depth.
- **HTML sanitization:** lightweight regex-based `EmailHtmlSafety` utility strips `<script>`, `<iframe>`, `<object>`, `<embed>`, `<form>`, meta refreshes, event handler attributes, `javascript:` URIs — no Jsoup dependency. Replaces the older `HtmlSanitizer`.
- **Font scaling:** multiplier (0.8x–1.3x) applied to WebView CSS font sizes, clamped to [10px, 28px] / [9px, 24px].
- Top bar actions: back, star/unstar, overflow menu (mark unread, archive, move to trash) — each navigates back after action.
- Reply and Forward buttons at the bottom.
- Thread connecting lines, relative timestamps, and alternating background tints between messages.
- Responsive attachment grid (2 columns on phones, 3-4 on tablets).

### PGP Encryption
- **End-to-end encryption:** compose and receive PGP-encrypted emails using OpenPGP via PGPainless.
- **Key management:** generate Ed25519/X25519 key pairs (optionally passphrase-protected), import ASCII-armored keys (with or without passphrase), export public keys, delete keys — all from the PGP Key Management screen in Settings.
- **Encrypt & sign:** toggle encryption and signing on outgoing emails from the compose toolbar. Encryption is per-recipient using stored public keys.
- **Auto-decryption:** incoming PGP/MIME and inline PGP messages are detected and decrypted automatically with signature verification. Passphrase-protected keys use stored passphrases transparently. Decrypted messages show a green lock badge + signature status; undecrypted blobs show a "PGP Encrypted" placeholder. Handles false-positive on non-PGP HTML content (e.g. invite emails).
- **Key storage:** private keys stored as armored files (encrypted via Android KeyStore AES-GCM), public keys as plain armored files, metadata in EncryptedSharedPreferences. Passphrases stored as encrypted blobs alongside private keys.

### Micro-interactions
The UI uses spring physics throughout for tactile, responsive feedback:
- **Settings category cards:** `graphicsLayer(scaleX, scaleY)` scales to 0.97 on press with `Spring.DampingRatioMediumBouncy`, restoring on release.
- **Support buttons:** same press-scale animation on `FilledTonalButton` / `OutlinedButton`.
- **Theme selector:** selected segment bounces to 1.04x scale; background/text colors animate with `tween(250)`.
- **Settings card content:** `animateContentSize(spring)` for smooth expand/collapse of nested settings.
- **Dock tabs:** `AnimatedDockTab` uses `updateTransition` for animated background, icon tint, and label width.
- **Sent overlay:** spring scale-in (dampingRatio 0.5, stiffness 300) and fade-in on send success.
- **Contact suggestions:** `fadeIn` + `expandVertically` / `fadeOut` + `shrinkVertically`.
- **Settings navigation:** slide + fade transitions between hub and sub-screens.
- **Compose undo window:** animated visibility expand/collapse.

### Welcome Box
- One-time overlay shown on first install with 7 action buttons: Ko-fi, UPI, GitHub star, Discord, Share, Report Issue, and Crypto donate (copy to clipboard).

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3 Expressive, Navigation Compose |
| Language | Kotlin 2.2 |
| Authentication | Google Credential Manager, MSAL 5.4.0, Google Play Services Auth |
| Networking | Retrofit 2, OkHttp 4 |
| Local Database | Room with SQLCipher encryption (v13) |
| Background Sync | WorkManager + ActionQueueManager (persistent action queue) |
| Image Loading | Coil Compose |
| IMAP/SMTP | Eclipse Angus Mail (Jakarta Mail 2.x) |
| OpenPGP | PGPainless 2.0.3 (Bouncy Castle-based encryption/signing) |
| Async | Kotlin Coroutines, Flow |
| Secure Storage | AndroidX Security Crypto (EncryptedSharedPreferences), Android KeyStore |
| Markdown Rendering | Markwon 4.6.2 |
| WebView | AndroidX WebKit 1.12.1 (algorithmic dark mode) |
| DI | Hilt 2.59.2 |

## Getting Started

### Prerequisites

- Android Studio Ladybug or later.
- A Google Cloud project with the Gmail API enabled.
- A Microsoft Azure App Registration with Outlook / Microsoft Graph API scopes (`Mail.Read`, `Mail.ReadWrite`, `Mail.Send`, `User.Read`) and a Mobile/Desktop platform redirect URI (`msal{client_id}://auth`).
- JDK 17+.

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/shrivatsav-0/monomail.git
   ```

2. Create a `secrets.properties` file in the root project directory (for building the `playstore` flavor):
   ```properties
   GOOGLE_CLIENT_ID=your_web_client_id_here
   ```

3. Configure MSAL for Outlook (optional — skip if you only use Gmail):
   ```bash
   # Edit app/src/main/res/raw/msal_config.json with your Azure client ID:
   # {
   #   "client_id": "your_msal_client_id",
   #   "authorities": [{"type": "AAD", "audience": "AzureADandPersonalMicrosoftAccount"}],
   #   "redirect_uri": "msal{your_client_id}://auth"
   # }
   ```

4. Sync Gradle and run on a device or emulator running Android 8.0 (API 26) or above.

### Installing the APK (GitHub vs Play Store Builds)

MonoMail is built using two distinct distribution flavors to protect API secrets:
- **Play Store Build (`playstore`)**: Comes bundled with the developer's official Google OAuth Web Client ID.
- **GitHub Release Build (`github`)**: Excludes the developer's private OAuth Web Client ID. Note: Google Sign-In is temporarily disabled on GitHub builds while Google's OAuth verification is in progress. Once verification completes, it will be enabled. You can use Microsoft Outlook or IMAP/SMTP accounts without restriction in the meantime.

Download the latest release from the [Releases page](https://github.com/shrivatsav-0/monomail/releases/latest) and install directly. You may need to enable "Install unknown apps" in your device settings.

Minimum supported version: Android 8.0 (API 26).

## Contributing

Pull requests are welcome. For significant changes, open an issue first to discuss the proposal. See [CONTRIBUTING.md](.github/CONTRIBUTING.md) for guidelines.

```
Fork -> Branch -> Commit -> Pull Request
```

Automated builds and releases run via GitHub Actions on PR merge.

## Support

If you like this project and want to support its development, consider buying me a coffee. It is a great help for a student working on open source.

<a href='https://ko-fi.com/N4N2W53M5' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://storage.ko-fi.com/cdn/kofi3.png?v=6' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

## License

[GPL-3.0](./LICENSE) — free to use, modify, and distribute under the same terms.

---

Built by [Shrivatsav](https://github.com/shrivatsav-0)
