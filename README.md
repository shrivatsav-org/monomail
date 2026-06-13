# Monomail ✉️

Monomail is a modern, beautifully designed, and highly responsive Android email client built with **Jetpack Compose** and the **Gmail API**. It provides a frictionless and premium email experience centered around an offline-first architecture, buttery smooth animations, and intuitive swipe gestures.

## Features ✨
- **Offline-First Architecture**: Built using Room Database, changes are applied optimistically instantly and synced with the Gmail API in the background.
- **Premium UI & Fluid Animations**: Designed with Material 3. Features like the top SearchBar morphing into toasts, cross-fading avatars, and `FastOutSlowInEasing` navigation transitions ensure the app feels top-tier.
- **Background Synchronization**: Integrated with Android's WorkManager. Incoming notifications and email status changes happen behind the scenes flawlessly.
- **Intuitive Gestures**:
  - Swipe Right to Archive / Unarchive.
  - Swipe Left to Star / Unstar.
  - Long Press to selectively view an email and toggle its Read/Unread status.
- **Search capabilities**: Fully offline and online search seamlessly integrated into the navigation.
- **Smart "Undo" Actions**: Accidentally archived or deleted a thread? A morphing toast gives you 4 seconds to undo your action without initiating a network request.

## Technologies Used 🛠️
- **Jetpack Compose**: 100% Kotlin declarative UI.
- **Room Database**: Local caching for blazing-fast load times.
- **Kotlin Coroutines & Flow**: Reactive architecture ensuring UI reflects real-time local DB states.
- **Retrofit & OkHttp**: Robust networking logic for the Gmail API.
- **WorkManager**: Queue-based background processing for syncing edits securely to Google's servers.
- **Coil**: Smooth, performant image loading for user profile avatars.

## Getting Started 🚀
1. Clone the repository.
2. Ensure you have your `secrets.properties` file with your `GOOGLE_CLIENT_ID` defined in the root project folder.
3. Sync Gradle and run the app.

## Screenshots 📸
*(Add your screenshots here)*

---
*Developed with focus on beautiful design and high performance.*
