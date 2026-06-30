# MonoMail

A monochrome email client for Android, built with Jetpack Compose and Material 3 Expressive. No colour accents, no noise, just email.

[Website](https://monomail.millosaurs.me) · [Download APK](https://github.com/shrivatsav-0/monomail/releases/latest) · [Discord](https://discord.gg/monomail)

> **⚠️ Gmail API Warning:** This app uses Gmail API OAuth verification which is limited to **100 test users**. The app is not published on the Play Store (cannot afford the developer account fee), so Google will show **"This app is blocked"** for any user beyond the first 100. If you encounter this, the test user quota has been exhausted. You can either run the app from source with your own Google Cloud project credentials, or use an Outlook account instead (no such limit).

---

## Overview

MonoMail is an open-source Android email client that connects to **Gmail** and **Microsoft Outlook** via OAuth and prioritises a distraction-free reading experience. The design system is intentionally monochrome -- every screen uses only black, white, and greyscale, built on top of Material 3 Expressive.

The architecture is offline-first: all reads and writes go through a local Room database encrypted with SQLCipher, and background sync via WorkManager keeps the remote state consistent without blocking the UI.

## Features

### Inbox
- Configurable dock bar: primary tabs in a pill + expandable flyout for remaining tabs. Customize in Settings (reorder, add up to 4, remove). Default: Unified, Sent, Archived.
- Folder tabs: Inbox, Sent, Archived, Starred, Trash, Snoozed, Spam, and Unified Inbox (when enabled).
- Snooze: long-press a thread to snooze (1hr, tonight, tomorrow, weekend, next week). Thread returns when time expires. Snoozed tab lists all snoozed threads.
- Spam folder: dedicated tab with report-not-spam and empty-spam actions. Pull-to-refresh fetches from server.
- Pull-to-refresh with embedded progress indicator in the search bar.
- Infinite scroll with pagination for large mailboxes.
- Date headers ("Today", "Yesterday", "Earlier") to organise the list chronologically.
- Smart grouping: auto-collapses threads from the same sender (threshold: 3 emails within 24 hours or 3 days) into expandable group headers showing sender name, count, and unread badge.
- Swipe-to-dismiss gestures on each thread: left and right swipe actions are independently configurable (archive, star, delete, mark read/unread), with colour-coded backgrounds.
- Long-press contextual overlay with blurred background and four action buttons: star/unstar, archive/unarchive (context-aware), mark read/unread, and delete/restore (context-aware).
- Undo toast with a 4-second window for archive, delete, snooze, and send actions; dismiss to execute immediately.
- **Bulk multi-select mode:** long-press any sender avatar to enter bulk mode with animated checkbox transitions, range select (tap body selects a range from the last toggled item), and select all/deselect all toggle — all with smooth animations.
- Mark all as read button in the search bar.
- Calendar badge in the search bar showing pending scheduled messages count.
- Sender avatar with domain favicon (loaded via Coil with initial fallback).
- Unread indicator: blue dot on avatar, bold sender/subject text, background tint.
- Message count badge next to sender name for threads with multiple messages.
- Relative timestamps: time (today), day name (this week), date (this year), full date (older).
- Font scaling (5 levels) and compact list mode.

### Conversation and Chain Views
- Toggle in Settings between Conversation View (default) and Message Chain view.
- Conversation View: collapsible message list. Only the latest message is expanded; older messages show a snippet and expand on tap with animated transitions. Subject and message count displayed at the top.
- Message Chain View: all messages expanded inline as a continuous scrollable list with full sender information for each message.
- Unread indicators in chain view: blue dot on avatar, bold sender name, blue bullet on date.
- Message count badge next to sender name in chain view.

### Search
- Local client-side search filters threads by subject, sender name, or snippet in real time.
- Server-side search triggers the Gmail or Outlook API, with paginated results and a "Search server for older emails" prompt when no local results are found.

### Multi-Account and Unified Inbox
- Support for up to 10 Gmail and Microsoft Outlook accounts simultaneously.
- Unified Inbox toggle in Settings → Dock Bar to see all accounts in a single combined tab.
- Quick account switching: horizontal swipe gesture on the profile avatar, or via the account switch modal.
- Profile card with avatar, display name, email, linked accounts count, and quick links to Settings and sign out.

### Compose
- New email, reply, and forward modes with pre-filled recipient and subject fields.
- CC and BCC fields with expand/collapse animation.
- Contact auto-suggestions indexed from threads and messages (debounced, max 5 results, case-insensitive matching).
- File attachments via system file picker (any MIME type), displayed as preview cards with thumbnails for images and icon with filename for other files. Removable via close button.
- Attachments are base64-encoded and sent as multipart MIME.
- Validation: recipient required, body cannot be empty.
- Confirmation-before-send option in Settings.
- Default reply mode (Reply / Reply All) configurable in Settings.
- **Schedule send:** pick a date & time to send later; attachments are cached and restored at send time. View/edit/cancel scheduled messages from the calendar icon in the search bar.
- **Undo send:** immediate send with a "Undo for Ns" live countdown in the search bar. Configurable undo window (5–30s).
- **Email templates:** save frequently-used bodies as templates. Apply from a bottom sheet in compose; manage in Settings.

### Settings
Accessible from the profile card. All settings are persisted via DataStore Preferences.

**Appearance:**
- Theme: System, Light, or Dark with animated transitions.
- Font Size: Extra Small, Small, Default, Large, Extra Large (0.7x to 1.3x scaling) with live preview.
- Show Dividers: toggle lines between email items.
- Compact List: reduce spacing in the email list.
- Show Snippet Preview: display preview text below sender in the inbox.
- All icons use the Material Rounded variant for a consistent, soft visual style.

**Dock Bar:**
- Unified Inbox: combine all accounts into one tab.
- Navigation Size: slider from 0.6x to 1.4x to scale the dock bar and action button.
- Dock Bar Editor: reorder primary tabs (up/down), add tabs from the available list (max 4), remove tabs (min 1). UNIFIED tab auto-hides when Unified Inbox is disabled.

**Behavior:**
- Conversation View / Message Chain: toggle between collapsible threads and expanded chain view in email detail.
- Smart Grouping: auto-group threads by sender in the inbox.
- Group Recent Only: restrict smart grouping to the last 24 hours (otherwise 3 days). Enabled by default.
- Swipe Left / Swipe Right: independently configure gesture actions (Archive, Star, Delete, Mark Read/Unread) via bottom sheet pickers.
- Confirm Before Sending: show a confirmation dialog before sending each email.
- Default Reply: Reply or Reply All.

**Sending:**
- Undo Send: toggle + window duration picker (5s, 10s, 20s, 30s).

**Templates:**
- Add, edit, and delete email templates. Apply from the compose screen.

**Notifications:**
- Email Notifications: master toggle for notification alerts.
- Sync Frequency: 15 minutes, 30 minutes, 1 hour, or Manual (polling only; no push).

**Updates:**
- Check for Updates: queries the GitHub releases API and shows status. Tapping opens the download page.

**About:**
- Version (1.3.9), Privacy Policy, Terms of Service, and Open Source Licenses.

**Support:**
- Ko-fi, UPI, GitHub star, Discord, Share, Report Issue, Crypto donate.

### Account Management
- Google Sign-In via Android Credential Manager and Google Identity Services.
- Microsoft Sign-In via MSAL (Microsoft Authentication Library).
- Account credentials stored encrypted with AES-GCM via Android KeyStore and EncryptedSharedPreferences.
- Session restoration on app launch.
- Provider-specific sign-out (clears Google Credential Manager or MSAL session independently).
- Add account dialog with Google and Microsoft provider selection.

### Notifications
- Adaptive background sync via WorkManager: polls every ~2 minutes when app is recently active, backs off to 15 minutes when idle.
- New email notification per account with sender name, subject, and system email icon.
- Android 13+ respects POST_NOTIFICATIONS permission.
- Per-account notification channels.
- **Inline reply** from the notification shade via RemoteInput.
- **Archive + undo** from the notification shade: archive action posts a follow-up undo notification.
- Timestamp-based detection to notify on replies within existing threads.

### Email Detail
- HTML body rendering via WebView with JavaScript disabled.
- Collapsible quoted text: hidden by default with a "Show quoted text" toggle and animated reveal — keeps the view clean while preserving full context on demand.
- Custom CSS injection for consistent typography — uses luminance-based dark mode detection for accurate theme matching regardless of system setting.
- Snippet previews strip quoted text ("On ... wrote:" patterns, `>` lines, and blockquote HTML) for cleaner inbox previews.
- CC and BCC recipients displayed in a styled container in the expanded message section, hidden by default until the user expands the message.
- Conversation view headers show sender avatar, name, From/To email addresses, and relative timestamps.
- Inline image attachments decoded and displayed as previews (max 280dp height) with loading spinner; click to open in system app.
- File attachments displayed in a responsive grid (columns adapt to screen width) with file extension badge, name, and size; download and open via FileProvider.
- Top bar actions: back, star/unstar, overflow menu (mark unread, archive, move to trash) -- each navigates back after action.
- Reply and Forward buttons at the bottom.

### Welcome Box
- One-time overlay shown on first install with 7 action buttons: Ko-fi, UPI, GitHub star, Discord, Share, Report Issue, and Crypto donate (copy to clipboard).

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3 Expressive, Navigation Compose |
| Language | Kotlin 2.2 |
| Authentication | Google Credential Manager, MSAL 5.4.0, Google Play Services Auth |
| Networking | Retrofit 2, OkHttp 4 |
| Local Database | Room with SQLCipher encryption |
| Background Sync | WorkManager |
| Image Loading | Coil Compose |
| Async | Kotlin Coroutines, Flow |
| Secure Storage | AndroidX Security Crypto (EncryptedSharedPreferences), Android KeyStore |
| Markdown Rendering | Markwon 4.6.2 |

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

Pull requests are welcome. For significant changes, open an issue first to discuss the proposal.

```
Fork -> Branch -> Commit -> Pull Request
```

## Support

If you like this project and want to support its development, consider buying me a coffee. It is a great help for a student working on open source.

<a href='https://ko-fi.com/N4N2W53M5' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://storage.ko-fi.com/cdn/kofi3.png?v=6' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

## License

[GPL-3.0](./LICENSE) -- free to use, modify, and distribute under the same terms.

---

Built by [Shrivatsav](https://github.com/shrivatsav-0)
