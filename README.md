# Monomail

A monochrome Gmail client for Android, built with Jetpack Compose and Material 3. No colour accents, no noise, just email.

[Website](https://monomail.millosaurs.me) · [Download APK](https://github.com/shrivatsav-0/monomail/releases/latest)

---

## Overview

Monomail is an open-source Android email client that connects to Gmail and Microsoft Outlook via OAuth and prioritises a distraction-free reading experience. The design system is intentionally monochrome — every screen uses only black, white, and greyscale, built on top of Material 3 Expressive with Google Sans and Roboto.

The architecture is offline-first: all reads and writes go through a local Room database first, and background sync via WorkManager keeps the remote state consistent without blocking the UI.

## Features

**Inbox**
- Pull-to-reveal loader that physically shifts inbox content to expose a refresh indicator.
- Swipe right to archive or unarchive a thread.
- Swipe left to star or unstar a thread.
- Long-press contextual menu to star, archive, mark unread, or delete.
- Smart undo toast with a 4-second window that cancels the network request if triggered.
- Configurable inbox grouping by sender with expandable group headers.
- Message count badge displayed next to sender name for threads with multiple messages.

**Conversation and Chain Views**
- Toggle between conversation view (collapsible grouped threads) and message chain view (all messages expanded inline) in Settings.
- Conversation view groups emails by subject with collapsible dropdowns per message.
- Chain view renders all messages in a thread as a single scrollable list with sender avatars, names, and timestamps.
- Unread indicators in chain view: blue dot on sender avatar, bold sender name, and blue bullet on date.
- Message count badge displayed next to sender name in chain view.

**Email View**
- Full thread rendering with inline attachments.
- Distraction-free compose with reply and forward support.
- Case-insensitive subject prefix stripping (Re:, Fwd:, Fw:) when replying and forwarding.
- Attachments displayed as clickable chips with file size and MIME type info.
- HTML rendering via WebView with dark mode support.

**Search**
- Offline search across cached threads.
- Online search falling back to the Gmail and Outlook APIs.

**Multi-Account and Unified Inbox**
- Support for multiple Gmail and Microsoft Outlook accounts simultaneously.
- Dedicated Unified Inbox view to see all emails from all accounts in one place.
- Quick account switching by swiping vertically on the profile avatar.
- Floating navigation bar to toggle between Primary Inbox and Unified Inbox.

**Account**
- Google and Microsoft Sign-In; credentials never leave the device.
- Profile modal with account switching support and multi-provider "Add Account" dialog.
- Support for up to 10 connected accounts simultaneously.

**Notifications**
- Background polling via WorkManager with notification on new emails.
- Notification detects replies in existing threads (timestamp-based comparison).
- Configurable notification preferences per account.

**Settings**
- Conversation view toggle (collapsible grouped threads vs. expanded message chain).
- Configurable swipe gesture actions (archive, star, delete, mark read/unread).
- Smart grouping toggle to auto-group inbox threads by sender.
- Show/hide email snippets and list dividers.
- Compact list mode and global font scaling across the entire app interface.
- In-app update checker to directly download the latest GitHub releases.
- Notification preferences per account.

**Performance and Stability**
- Coil-based avatar loading with favicon fallback for sender domain.
- Optimised Room queries with proper indexes on threadId, date, and tab.
- In-memory deduplication and ID-based keying for lazy lists.
- Haptic feedback on long-press interactions

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Language | Kotlin 100% |
| Auth | Google Credential Manager, MSAL (Microsoft Authentication Library), OAuth 2.0 |
| Networking | Retrofit, OkHttp |
| Local storage | Room |
| Background sync | WorkManager |
| Image loading | Coil |
| Async | Kotlin Coroutines, Flow |

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later.
- A Google Cloud project with the Gmail API enabled.
- A Microsoft Azure App Registration with Outlook/Graph API scopes enabled.
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

[GPL-3.0](./LICENSE) — free to use, modify, and distribute under the same terms.

---

Built by [Shrivatsav](https://github.com/shrivatsav-0)
