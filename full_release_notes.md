## What's Changed

### Features & Improvements
- **Email Loading:** Optimized email detail loading with asynchronous HTML parsing, WebView fade-in animations, and pre-warmed Chromium instances for smoother reading.
- **Architecture:** Completed a major architectural overhaul, modularizing the application into distinct `core` and `feature` modules for better maintainability and optimized build times.
- **Distribution:** Abstracted authentication logic to support specific builds (like GitHub vs. Play Store). 

### Bug Fixes
- **Launch Crash:** Fixed a critical crash on launch related to missing HiltWorker processing and incorrect resource identifiers.
- **Notifications:** Restored the custom Monomail notification icon that was previously falling back to the generic Android icon.
- **Settings:** The Settings screen now accurately displays the application version directly from the Android `PackageManager`.
- **Release Build:** Fixed a resource linking failure that was preventing successful release builds in the `auth` feature module.
- **Release Pipeline:** Fixed several automated release workflow issues, including the generation of accurate Play Store release notes directly from Pull Request descriptions and previous release context.

### Maintenance
- Removed deprecated `FORCE_DARK` and `FORCE_LIGHT` email theme options.
- Updated Play Store automated deployment tracks and stripped unsupported language tags from release notes.
