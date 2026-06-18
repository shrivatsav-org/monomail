# MonoMail 1.2.7 Release Notes

## New

- **Navigation size slider** — make the bottom dock tabs smaller or larger (Settings → Appearance → Navigation Size)

## What's Improved

- **App feels noticeably faster** — navigation between screens, modal popups, and the search bar are all more responsive
- **Dock tabs are smoother** — switching between Inbox, Sent, Archived, and Trash no longer stutters or lags
- **Email list scrolls more smoothly** — sender avatars (favicons) load faster, less jank while scrolling
- **Email detail screen loads faster** — the HTML body is cached so switching between emails is instant

## Bugs Fixed

- **Undo button now works** — the undo toast that appears after archiving/deleting was not clickable; tapping it now properly restores the conversation
- **Sign out only signs out the current account** — previously signing out would remove all accounts; now it only signs out the one you're using
- **Going back from a tab returns to inbox** — pressing back from Compose, Settings, or Email Detail now correctly navigates to the inbox instead of getting stuck
- **Account list updates automatically** — adding or switching accounts no longer requires a manual refresh
- **Dock labels no longer cut off** — tab labels like "Archived" display fully without truncation
