CREATE TABLE IF NOT EXISTS licenses (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  key TEXT UNIQUE NOT NULL,
  email TEXT NOT NULL,
  plan TEXT DEFAULT 'premium',
  status TEXT DEFAULT 'active',
  expiresAt INTEGER,
  createdAt INTEGER NOT NULL,
  revokedAt INTEGER
);
