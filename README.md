# MonoMail

A monochrome email client for Android, built with Jetpack Compose and Material 3. No colour accents, no noise, just email.

[Website](https://monomail.millosaurs.me) · [Download APK](https://github.com/shrivatsav-0/monomail/releases/latest)

---

## Overview

MonoMail is an open-source Android email client that connects to Gmail and Microsoft Outlook via OAuth and prioritises a distraction-free reading experience. The design system is intentionally monochrome -- every screen uses only black, white, and greyscale, built on top of Material 3 Expressive.

The architecture is offline-first: all reads and writes go through a local Room database encrypted with SQLCipher, and background sync via WorkManager keeps the remote state consistent without blocking the UI.

## Features

### Inbox
- Six folder tabs: Inbox, Sent, Archived, Starred, Trash, and Unified Inbox (when enabled).
- Pull-to-refresh to sync the current folder.
- Infinite scroll with pagination for large mailboxes.
- Date headers ("Today", "Yesterday", "Earlier") to organise the list chronologically.
- Smart grouping: auto-collapses threads from the same sender (threshold: 3 emails within 24 hours or 3 days) into expandable group headers showing sender name, count, and unread badge.
- Swipe-to-dismiss gestures on each thread: left and right swipe actions are independently configurable (archive, star, delete, mark read/unread), with colour-coded backgrounds.
- Long-press contextual overlay with blurred background and four action buttons: star/unstar, archive/unarchive (context-aware), mark read/unread, and delete/restore (context-aware).
- Undo toast with a 4-second window for archive and delete actions; dismiss to execute immediately.
- Mark all as read button in the search bar.
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
- Unified Inbox toggle in Settings to see all accounts in a single combined tab.
- Quick account switching: vertical drag gesture on the profile avatar, or via the account switch modal.
- Profile card with avatar, display name, email, linked accounts count, and quick links to Starred, Trash, Settings, and sign out.

### Compose
- New email, reply, and forward modes with pre-filled recipient and subject fields.
- Contact auto-suggestions indexed from threads and messages (debounced, max 5 results, case-insensitive matching).
- File attachments via system file picker (any MIME type), displayed as preview cards with thumbnails for images and icon with filename for other files. Removable via close button.
- Attachments are base64-encoded and sent as multipart MIME.
- Validation: recipient required, body cannot be empty.
- Confirmation-before-send option in Settings.
- Default reply mode (Reply / Reply All) configurable in Settings.

### Settings
Accessible from the profile card. All settings are persisted via DataStore Preferences.

**Appearance:**
- Theme: System, Light, or Dark with animated transitions.
- Font Size: Extra Small, Small, Default, Large, Extra Large (0.7x to 1.3x scaling) with live preview.
- Show Dividers: toggle lines between email items.
- Compact List: reduce spacing in the email list.
- Show Snippet Preview: display preview text below sender in the inbox.

**Behavior:**
- Unified Inbox: combine all accounts into one tab.
- Conversation View / Message Chain: toggle between collapsible threads and expanded chain view in email detail.
- Smart Grouping: auto-group threads by sender in the inbox.
- Group Recent Only: restrict smart grouping to the last 24 hours (otherwise 3 days).
- Swipe Left / Swipe Right: independently configure gesture actions (Archive, Star, Delete, Mark Read/Unread) via bottom sheet pickers.
- Confirm Before Sending: show a confirmation dialog before sending each email.
- Default Reply: Reply or Reply All.

**Notifications:**
- Email Notifications: master toggle for notification alerts.
- Sync Frequency: 15 minutes, 30 minutes, 1 hour, or Manual (polling only; no push).

**Updates:**
- Check for Updates: queries the GitHub releases API and shows status (Checking, Up to Date, Update Available). Tapping opens the download page.

**About:**
- Version (1.2.5), Privacy Policy, Terms of Service, and Open Source Licenses.

**Support:**
- Link to Ko-fi donation page.

### Account Management
- Google Sign-In via Android Credential Manager and Google Identity Services.
- Microsoft Sign-In via MSAL (Microsoft Authentication Library).
- Account credentials stored encrypted with AES-GCM via Android KeyStore and EncryptedSharedPreferences.
- Session restoration on app launch.
- Provider-specific sign-out (clears Google Credential Manager or MSAL session independently).
- Add account dialog with Google and Microsoft provider selection.

### Notifications
- Background periodic sync via WorkManager (respects the configured sync frequency).
- New email notification per account with sender name, subject, and system email icon.
- Android 13+ respects POST_NOTIFICATIONS permission.
- Notification channel: "New Emails".
- Timestamp-based detection to notify on replies within existing threads.

### Email Detail
- HTML body rendering via WebView with JavaScript disabled.
- Quoted text removal: strips Gmail, Yahoo, and Mozilla quoting blocks, "On ... wrote:" patterns, and `>` quoted lines.
- Custom CSS injection for consistent typography and dark mode.
- Inline image attachments decoded and displayed as previews (max 280dp height) with loading spinner; click to open in system app.
- File attachments displayed in a 2-column grid with file extension badge, name, and size; download and open via FileProvider.
- Top bar actions: back, star/unstar, overflow menu (mark unread, archive, move to trash) -- each navigates back after action.
- Reply and Forward buttons at the bottom.

### Donation Prompt
- One-time overlay shown on first install linking to the Ko-fi page, dismissible via a close button.

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Language | Kotlin |
| Authentication | Google Credential Manager, MSAL 5.4.0, Google Play Services Auth |
| Networking | Retrofit 2, OkHttp 3 |
| Local Database | Room with SQLCipher encryption |
| Background Sync | WorkManager |
| Image Loading | Coil Compose |
| Async | Kotlin Coroutines, Flow |
| Secure Storage | AndroidX Security Crypto (EncryptedSharedPreferences), Android KeyStore |
| Markdown Rendering | Markwon 4.6.2 |

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later.
- A Google Cloud project with the Gmail API enabled.
- A Microsoft Azure App Registration with Outlook / Graph API scopes enabled.
- An OAuth 2.0 Web Client ID for Google and Client ID for Microsoft.

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/shrivatsav-0/monomail.git
   ```

2. Create a `secrets.properties` file in the root project directory:
   ```properties
   GOOGLE_CLIENT_ID=your_web_client_id_here
   ```

3. Sync Gradle and run on a device or emulator running Android 8.0 (API 26) or above.

### Installing the APK

Download the latest release from the [Releases page](https://github.com/shrivatsav-0/monomail/releases/latest) and install directly. You may need to enable "Install unknown apps" in your device settings.

Minimum supported version: Android 8.0 (API 26).

## Screenshots

| Sign In | Inbox | Email |
|---|---|---|
| ![Sign In](https://monomail.millosaurs.me/6.png) | ![Inbox](https://monomail.millosaurs.me/1.png) | ![Email](https://monomail.millosaurs.me/2.png) |

| Long Press | Profile | Settings |
|---|---|---|
| ![Long Press](https://monomail.millosaurs.me/3.png) | ![Profile](https://monomail.millosaurs.me/4.png) | ![Settings](https://monomail.millosaurs.me/5.png) |

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
