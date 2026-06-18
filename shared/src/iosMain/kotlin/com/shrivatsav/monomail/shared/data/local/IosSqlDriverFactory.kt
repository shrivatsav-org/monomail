package com.shrivatsav.monomail.shared.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.shrivatsav.monomail.shared.database.AppDatabase

/**
 * iOS SQL driver. The on-disk DB file is protected at rest via the app's
 * Data Protection entitlement (NSFileProtectionComplete) rather than SQLCipher.
 */
class IosSqlDriverFactory : SqlDriverFactory {
    override fun create(): SqlDriver =
        NativeSqliteDriver(AppDatabase.Schema, "monomail.db")
}
