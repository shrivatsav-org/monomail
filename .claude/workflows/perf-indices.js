export const meta = {
  name: 'room-indices',
  description: 'Add database indices to Room entities',
  phases: [{ title: 'Indices', detail: 'Add indices to Entity classes' }],
}

phase('Indices')
await agent(
  'Add database indices to all Room entity classes in Entities.kt at:\n' +
  'app/src/main/java/com/shrivatsav/monomail/data/local/Entities.kt\n\n' +
  'Current context:\n' +
  '- ThreadEntity (table: threads): has threadId (PK), accountId, inInbox, inSent, inArchived, inTrash, inSpam, isSnoozed, isStarred, date.\n' +
  '  Queries filter by accountId + folder booleans, sort by date DESC.\n' +
  '- EmailEntity (table: emails): has id (PK), accountId, threadId, inInbox, inSent, etc. Same filter patterns.\n' +
  '- PendingActionEntity (table: pending_actions): has id (PK), status.\n\n' +
  'Rules:\n' +
  '1. Add composite indices for the most common query patterns.\n' +
  '2. For ThreadEntity, add indices on: (accountId, inInbox, date), (accountId, inSent, date),\n' +
  '   (accountId, inArchived, date), (accountId, isStarred, date), (accountId, inTrash, date),\n' +
  '   (accountId, inSpam, date), (accountId, isSnoozed, snoozedUntil)\n' +
  '3. For EmailEntity, add indices on: (accountId, threadId), (accountId, inInbox, date),\n' +
  '   (accountId, inSent, date), (accountId, inArchived, date), (accountId, isStarred, date),\n' +
  '   (accountId, inTrash, date), (accountId, inSpam, date), (accountId, isSnoozed, snoozedUntil),\n' +
  '   (threadId, accountId)\n' +
  '4. For PendingActionEntity, add index on (status)\n' +
  '5. Keep @Entity(tableName = "...") annotation, just add indices = [...] to it\n' +
  '6. Do NOT change any other code, fields, or functions\n' +
  'Read the file first, then make the edits.',
  { label: 'Room Indices', phase: 'Indices', isolation: 'worktree' }
)
