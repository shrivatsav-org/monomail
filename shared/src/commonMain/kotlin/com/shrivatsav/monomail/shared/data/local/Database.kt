package com.shrivatsav.monomail.shared.data.local

import app.cash.sqldelight.db.SqlDriver
import com.shrivatsav.monomail.shared.database.AppDatabase

/**
 * Platform-specific SQL driver creation. Implemented per platform:
 * Android -> AndroidSqliteDriver (SQLCipher-backed for at-rest encryption),
 * iOS -> NativeSqliteDriver (DB file protected via NSFileProtectionComplete).
 */
interface SqlDriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: SqlDriverFactory): AppDatabase =
    AppDatabase(factory.create())
