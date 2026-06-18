# MonoMail 1.3.2 Release Notes

## New Features

### Redesigned Bottom Navigation Dock
- All-new animated dock with smoother tab switching (Inbox, Sent, Archived, Trash)
- Tab labels animate in and out when selected
- New **Navigation Size** setting to adjust dock size from 0.6× to 1.4× (Settings → Appearance)

### Dynamic Action Button
- The FAB intelligently changes based on the current folder
- In Inbox/Sent/Archived: compose icon to write a new email
- In Trash: changes to an **"Empty" button** that lets you clear your trash in one tap

### Swipe Gesture on Profile menu
- Swipe horizontally on the profile avatar in the profile menu to quickly switch between accounts

### Unified Undo Toast
- Archiving or deleting a conversation shows a cleaner, more noticeable undo bar at the top
- The undo button is now properly clickable — tap it to instantly restore the last action

## UI/UX Improvements

- **App feels snappier** — navigation between screens, modal popups, and the search bar are all more responsive
- **Smoother scrolling** — sender avatars load faster, less jank while scrolling through the inbox
- **Faster email detail** — switching between emails in a thread is instant with cached HTML rendering
- **Cleaner animations** — dock tabs, modals, and the action button all use smoother, faster transitions

## Bugs Fixed

- **Undo button now works** — the undo toast was not clickable; tapping it now properly restores the conversation
- **Sign-out only signs out the current account** — previously signing out would remove all accounts; now it only signs out the one you're using
- **Back navigation fixed** — pressing back from Compose, Settings, or Email Detail now correctly returns to the inbox
- **Account list updates automatically** — adding or switching accounts no longer requires a manual refresh
- **Dock labels no longer cut off** — tab labels display fully without truncation
