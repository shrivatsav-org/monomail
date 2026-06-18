package com.shrivatsav.monomail.shared.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.shrivatsav.monomail.shared.database.AppDatabase

/**
 * Android SQL driver.
 * TODO(security): wrap with a SQLCipher SupportFactory + key from AndroidSecureStore
 * to restore the original at-rest encryption posture.
 */
class AndroidSqlDriverFactory(private val context: Context) : SqlDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(AppDatabase.Schema, context, "monomail.db")
}
