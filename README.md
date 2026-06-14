# Monomail

A pure black-and-white Gmail client for Android, built with Jetpack Compose and Material 3. No colour accents, no noise — just email.

**[Website](https://monomail.millosaurs.me) · [Download APK](https://github.com/shrivatsav-0/monomail/releases/latest)**

---

## Overview

Monomail is an open-source Android email client that connects to Gmail via OAuth and prioritises a distraction-free reading experience. The design system is intentionally monochrome — every screen uses only black, white, and greyscale — built on top of Material 3 Expressive with Google Sans and Roboto.

The architecture is offline-first: all reads and writes go through a local Room database first, and background sync via WorkManager keeps the remote state consistent without blocking the UI.

## Features

**Inbox**
- Pull-to-reveal loader that physically shifts inbox content to expose a refresh indicator
- Swipe right to archive or unarchive a thread
- Swipe left to star or unstar a thread
- Long-press contextual menu to star, archive, mark unread, or delete
- Smart undo toast with a 4-second window that cancels the network request if triggered

**Email view**
- Full thread rendering with inline attachments
- Distraction-free compose with reply and forward support

**Search**
- Offline search across cached threads
- Online search falling back to the Gmail API

**Account**
- Google Sign-In via Credential Manager — credentials never leave the device
- Profile modal with account switching support

**Settings**
- Configurable swipe gesture actions
- Notification preferences

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Language | Kotlin 100% |
| Auth | Google Credential Manager, OAuth 2.0 |
| Networking | Retrofit, OkHttp |
| Local storage | Room |
| Background sync | WorkManager |
| Image loading | Coil |
| Async | Kotlin Coroutines, Flow |

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- A Google Cloud project with the Gmail API enabled
- An OAuth 2.0 Web Client ID

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

Download the latest release from the [Releases page](https://github.com/shrivatsav-0/monomail/releases/latest) and install directly. You may need to enable **Install unknown apps** in your device settings.

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
Fork → Branch → Commit → Pull Request
```

## License

[GPL-3.0](./LICENSE) — free to use, modify, and distribute under the same terms.

---

Built by [Shrivatsav](https://github.com/shrivatsav-0)
