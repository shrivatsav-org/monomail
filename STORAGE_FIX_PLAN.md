# Email Storage & Stale Data Plan

## Problem Summary

Two interrelated issues:

1. **Emails stored in userData, not cache**: The Room database lives at the default `databases/monomail_database` path inside Android's internal app data (`/data/data/<pkg>/databases/`). This is **userData** storage — the OS never auto-evicts it, and "Clear Cache" from Android Settings does nothing to it. The only recourse is "Clear Data" which resets the entire app (wipes auth tokens, settings, everything).

2. **No server-side reconciliation**: Email sync is append/upsert-only. `refreshInbox()` fetches 20 threads and upserts them via `INSERT OR REPLACE`. It **never** compares local thread IDs against server thread IDs. Stale/orphaned emails (deleted on server or from another client) persist locally forever. The **only** cleanup path is reactive: when a user opens a specific dead thread, `refreshThread()` catches `ResourceNotFoundException` and deletes it locally.

## Root Cause Analysis

| Component | Issue |
|-----------|-------|
| `AppDatabase.kt` | `Room.databaseBuilder(context, ..., "monomail_database")` — no custom path, defaults to userData |
| `EmailRepository.refreshInbox()` | Fetches 20 threads, upserts, never removes orphans |
| `EmailSyncWorker` | Calls `refreshInbox(INBOX)` only — no reconciliation pass |
| `ThreadDao` / `EmailDao` | No "delete where threadId NOT IN (...)" queries exist |
| `refreshThread()` | Only reactive cleanup — requires user to open a dead thread |

## Proposed Fix (3 Phases)

### Phase 1: Server Reconciliation (Core Fix)

**Goal**: During each sync, detect and remove local threads/emails that no longer exist on the server.

**Changes**:

1. **`ThreadDao.kt`** — Add reconciliation query:
   ```kotlin
   @Query("DELETE FROM threads WHERE accountId = :accountId AND inInbox = 1 AND threadId NOT IN (:serverThreadIds)")
   suspend fun deleteOrphanedThreads(accountId: String, serverThreadIds: List<String>)
   ```
   Plus matching `EmailDao` cleanup:
   ```kotlin
   @Query("DELETE FROM emails WHERE threadId NOT IN (SELECT threadId FROM threads)")
   suspend fun deleteOrphanedEmails()
   ```

2. **`EmailRepository.refreshInbox()`** — After upserting the server response, call `deleteOrphanedThreads()` with the full list of server thread IDs for that folder. Then call `deleteOrphanedEmails()` to cascade.

3. **Per-thread reconciliation in `refreshThread()`** — Already catches `ResourceNotFoundException` for deleted threads. Additionally, reconcile the email list within a thread: after fetching the thread's emails from the server, delete any local emails whose IDs aren't in the server response. This directly fixes the "8 emails showing as 36" scenario.

**Consideration**: The current sync only fetches 20 threads per call. Reconciliation requires knowing the *complete* set of server thread IDs, not just one page. Options:
   - **Option A (Recommended)**: Fetch all thread IDs (lightweight — just IDs, not full messages) from the server in a separate API call, then diff against local. Gmail API supports `threads.list` with `fields=threads/id` for minimal bandwidth.
   - **Option B**: Only reconcile within the fetched page — less complete but zero extra API calls. Threads beyond page 1 that were deleted server-side would persist until scrolled to.
   - **Option C**: Full paginated fetch during periodic deep-sync (e.g., every 6 hours), lightweight page-1 reconciliation during normal sync.

### Phase 2: Periodic Database Maintenance

**Goal**: Prevent unbounded database growth regardless of sync behavior.

**Changes**:

1. **Age-based eviction**: Add a background maintenance task (WorkManager periodic, e.g., daily) that:
   - Deletes emails older than a configurable threshold (e.g., 90 days) from the local DB
   - Preserves starred/pinned threads
   - Runs `VACUUM` to reclaim space

2. **`ThreadDao.kt`** — Add maintenance queries:
   ```kotlin
   @Query("DELETE FROM threads WHERE accountId = :accountId AND lastMessageTimestamp < :cutoffTimestamp AND isStarred = 0")
   suspend fun deleteOldThreads(accountId: String, cutoffTimestamp: Long)
   ```

3. **Size monitoring**: Log database size periodically; if exceeding a threshold (e.g., 100MB), trigger aggressive cleanup.

### Phase 3: Manual Cache Clear (UX Fix)

**Why not move to cacheDir**: Moving the encrypted Room database to `cacheDir` is dangerous — Android may evict cache files at any time under storage pressure, which would silently destroy the user's entire local email store and search index.

**Instead**: Expose a **"Clear email cache"** option in Settings that:
   - Calls `database.clearAllTables()` to wipe all local email data
   - Preserves auth tokens, user preferences, and account configuration
   - Triggers a full re-sync afterward
   - Gives users the same "fix" as clearing app data without the nuclear reset

## Migration Strategy

- **Phase 1 is the critical fix** — it directly addresses the "36 emails in an 8-message thread" scenario and prevents future orphan accumulation.
- **Phase 2 is defense-in-depth** — prevents the database from growing without bound even if reconciliation misses edge cases.
- **Phase 3 is a UX improvement** — gives users a self-service recovery button.

## Files to Modify

| File | Phase | Change |
|------|-------|--------|
| `core/database/local/ThreadDao.kt` | 1, 2 | Add `deleteOrphanedThreads()`, `deleteOldThreads()` |
| `core/database/local/EmailDao.kt` | 1, 2 | Add `deleteOrphanedEmails()`, `deleteOldEmails()` |
| `core/data/repository/EmailRepository.kt` | 1 | Add reconciliation pass in `refreshInbox()` and `refreshThread()` |
| `core/network/provider/EmailProvider.kt` | 1 | Add `listAllThreadIds()` for lightweight full-ID fetch |
| `core/network/provider/gmail/GmailProvider.kt` | 1 | Implement `listAllThreadIds()` via Gmail API |
| `core/network/provider/OutlookProvider.kt` | 1 | Implement `listAllThreadIds()` via Graph API |
| `core/network/provider/imap/ImapProvider.kt` | 1 | Implement `listAllThreadIds()` |
| `core/data/worker/EmailSyncWorker.kt` | 2 | Add periodic maintenance trigger |
| `feature/settings/` | 3 | Add "Clear email cache" UI option |

## Risk Assessment

- **Phase 1 risk**: Low. Reconciliation only deletes local data that doesn't exist on the server. Worst case: a network error during ID fetch causes false positives → wrap in try/catch, skip reconciliation on error.
- **Phase 2 risk**: Low. Age-based eviction preserves starred items and is configurable.
- **Phase 3 risk**: None (additive UI only).
