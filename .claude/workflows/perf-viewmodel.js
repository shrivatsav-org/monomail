export const meta = {
  name: 'fix-viewmodel-perf',
  description: 'Fix side effect in state derivation and recomposition issues',
  phases: [{ title: 'ViewModel Fixes', detail: 'Fix EmailDetailViewModel + SwipeableEmailItem' }],
}

phase('ViewModel Fixes')
await agent(
  "Fix two performance issues across two files:\n\n" +
  "FILE 1: EmailDetailViewModel.kt\n" +
  "Path: app/src/main/java/com/shrivatsav/monomail/ui/screens/detail/EmailDetailViewModel.kt\n\n" +
  "Problem: The combine transformation for `state` calls repository.markEmailsAsRead(unreadIds) \n" +
  "INSIDE the lambda (lines ~39-41). This is a side effect inside state derivation — every \n" +
  "flow emission triggers a network+DB call.\n\n" +
  "Fix:\n" +
  "1. Remove these lines from inside the combine lambda:\n" +
  "   val unreadIds = emails.filter { !it.isRead }.map { it.id }\n" +
  "   if (unreadIds.isNotEmpty()) {\n" +
  "       repository.markEmailsAsRead(unreadIds)\n" +
  "   }\n" +
  "2. Add a new collector in the init block that watches state and marks unread emails as read:\n" +
  "   viewModelScope.launch {\n" +
  "       state.collect { s ->\n" +
  "           if (s is EmailDetailState.Success) {\n" +
  "               val unreadIds = s.emails.filter { !it.isRead }.map { it.id }\n" +
  "               if (unreadIds.isNotEmpty()) {\n" +
  "                   repository.markEmailsAsRead(unreadIds)\n" +
  "               }\n" +
  "           }\n" +
  "       }\n" +
  "   }\n" +
  "3. If there's an existing collector for contact photo URIs, replace it with this one (since we are removing contact photo code separately, just add this new collector).\n\n" +
  "FILE 2: SwipeableEmailItem.kt\n" +
  "Path: app/src/main/java/com/shrivatsav/monomail/ui/screens/inbox/SwipeableEmailItem.kt\n\n" +
  "Problem: thread.copy(isRead = optIsRead, isStarred = optIsStarred) is computed on every \n" +
  "recomposition at lines 83 and 162, creating a new EmailThread instance each time. Compose \n" +
  "can't skip recomposing EmailItem because the thread parameter reference is always different.\n\n" +
  "Fix:\n" +
  "Add a derived state that only recomputes when dependencies change:\n" +
  "   val displayThread by remember(thread, optIsRead, optIsStarred) {\n" +
  "       derivedStateOf { thread.copy(isRead = optIsRead, isStarred = optIsStarred) }\n" +
  "   }\n" +
  "Then replace:\n" +
  "   - thread.copy(isRead = optIsRead, isStarred = optIsStarred) with just displayThread\n" +
  "   - Do this for both EmailItem call sites (the one inside SwipeToDismissBox at line ~162 and the one outside at line ~83)\n\n" +
  "Also add import for derivedStateOf if not already imported:\n" +
  "   import androidx.compose.runtime.derivedStateOf\n\n" +
  "Read each file first before making changes.",
  { label: 'ViewModel Fixes', phase: 'ViewModel Fixes', isolation: 'worktree' }
)
