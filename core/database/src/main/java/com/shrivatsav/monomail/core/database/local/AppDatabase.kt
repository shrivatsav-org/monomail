package com.shrivatsav.monomail.core.database.local
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shrivatsav.monomail.security.SecurityUtil
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
@Database(
    entities = [ThreadEntity::class, EmailEntity::class, EmailFtsEntity::class, ScheduledMessageEntity::class, PendingActionEntity::class, PendingSendEntity::class],
    version = 18,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun emailDao(): EmailDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun pendingSendDao(): PendingSendDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            return INSTANCE ?: synchronized(this) {
                val passphrase = String(SecurityUtil.getDatabasePassphrase(context)).toByteArray()
                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monomail_database"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE threads ADD COLUMN accountId TEXT NOT NULL DEFAULT 'gmail_unknown'")
        db.execSQL("ALTER TABLE emails ADD COLUMN accountId TEXT NOT NULL DEFAULT 'gmail_unknown'")
    }
}
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emails ADD COLUMN ccEmail TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE emails ADD COLUMN bccEmail TEXT NOT NULL DEFAULT ''")
    }
}
// NOTE: This is intentionally a no-op. MIGRATION_3_4 already added ccEmail and bccEmail columns.
// Keeping this migration preserves the 4→5 migration path so users on DB v4 don't hit fallbackToDestructiveMigration.
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // No-op — columns already added by MIGRATION_3_4
    }
}
val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `scheduled_messages` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `fromEmail` TEXT NOT NULL, `to` TEXT NOT NULL, `cc` TEXT NOT NULL DEFAULT '', `bcc` TEXT NOT NULL DEFAULT '', `subject` TEXT NOT NULL, `body` TEXT NOT NULL, `attachmentsJson` TEXT NOT NULL DEFAULT '[]', `scheduledAt` INTEGER NOT NULL, `isSent` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}
val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE threads ADD COLUMN isSnoozed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE threads ADD COLUMN snoozedUntil INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE emails ADD COLUMN isSnoozed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE emails ADD COLUMN snoozedUntil INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE threads ADD COLUMN inSpam INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE emails ADD COLUMN inSpam INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_actions` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `actionType` TEXT NOT NULL, `threadId` TEXT NOT NULL, `payload` TEXT NOT NULL DEFAULT '', `emailIdsJson` TEXT NOT NULL DEFAULT '', `status` TEXT NOT NULL DEFAULT 'PENDING', `createdAt` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL DEFAULT 0, `errorMessage` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
    }
}
val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emails ADD COLUMN bodyIsHtml INTEGER NOT NULL DEFAULT 1")
    }
}
val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scheduled_messages ADD COLUMN fromAlias TEXT DEFAULT NULL")
    }
}
val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_threads_snooze ON threads(isSnoozed, snoozedUntil)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_emails_snooze ON emails(isSnoozed, snoozedUntil)")
    }
}
val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `threads_new` (
                `threadId` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `subject` TEXT NOT NULL,
                `fromName` TEXT NOT NULL,
                `fromEmail` TEXT NOT NULL,
                `snippet` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `messageCount` INTEGER NOT NULL,
                `isRead` INTEGER NOT NULL,
                `isStarred` INTEGER NOT NULL,
                `latestMessageId` TEXT NOT NULL,
                `participants` TEXT NOT NULL,
                `inInbox` INTEGER NOT NULL,
                `inSent` INTEGER NOT NULL,
                `inArchived` INTEGER NOT NULL,
                `inTrash` INTEGER NOT NULL,
                `isSnoozed` INTEGER NOT NULL DEFAULT 0,
                `snoozedUntil` INTEGER NOT NULL DEFAULT 0,
                `inSpam` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`accountId`, `threadId`)
            )
        """)
        db.execSQL("INSERT INTO `threads_new` SELECT * FROM `threads`")
        db.execSQL("DROP TABLE `threads`")
        db.execSQL("ALTER TABLE `threads_new` RENAME TO `threads`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_inInbox_date` ON `threads`(`accountId`, `inInbox`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_inSent_date` ON `threads`(`accountId`, `inSent`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_inArchived_date` ON `threads`(`accountId`, `inArchived`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_isStarred_date` ON `threads`(`accountId`, `isStarred`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_inTrash_date` ON `threads`(`accountId`, `inTrash`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_inSpam_date` ON `threads`(`accountId`, `inSpam`, `date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_accountId_isSnoozed_snoozedUntil` ON `threads`(`accountId`, `isSnoozed`, `snoozedUntil`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_threads_isSnoozed_snoozedUntil` ON `threads`(`isSnoozed`, `snoozedUntil`)")
    }
}
val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scheduled_messages ADD COLUMN threadId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE scheduled_messages ADD COLUMN messageId TEXT DEFAULT NULL")
    }
}
val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `emails_fts` USING FTS4(
                content=`emails`,
                `subject` TEXT,
                `body` TEXT,
                `fromName` TEXT,
                `fromEmail` TEXT,
                `toEmail` TEXT,
                `snippet` TEXT
            )
        """)
        db.execSQL("""
            INSERT INTO `emails_fts`(`docid`, `subject`, `body`, `fromName`, `fromEmail`, `toEmail`, `snippet`)
            SELECT `rowid`, `subject`, `body`, `fromName`, `fromEmail`, `toEmail`, `snippet` FROM `emails`
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_sends` (
                `id` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `fromEmail` TEXT NOT NULL,
                `to` TEXT NOT NULL,
                `cc` TEXT NOT NULL DEFAULT '',
                `bcc` TEXT NOT NULL DEFAULT '',
                `subject` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `attachmentsJson` TEXT NOT NULL DEFAULT '[]',
                `createdAt` INTEGER NOT NULL,
                `fromAlias` TEXT DEFAULT NULL,
                `threadId` TEXT DEFAULT NULL,
                `messageId` TEXT DEFAULT NULL,
                `messageReferences` TEXT DEFAULT NULL,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sends_accountId` ON `pending_sends`(`accountId`)")
    }
}

val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Identity hash changed between builds at version 15 — reconcile the schema.
        // The pending_sends table was already created by MIGRATION_14_15; IF NOT EXISTS is idempotent.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_sends` (
                `id` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `fromEmail` TEXT NOT NULL,
                `to` TEXT NOT NULL,
                `cc` TEXT NOT NULL DEFAULT '',
                `bcc` TEXT NOT NULL DEFAULT '',
                `subject` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `attachmentsJson` TEXT NOT NULL DEFAULT '[]',
                `createdAt` INTEGER NOT NULL,
                `fromAlias` TEXT DEFAULT NULL,
                `threadId` TEXT DEFAULT NULL,
                `messageId` TEXT DEFAULT NULL,
                `messageReferences` TEXT DEFAULT NULL,
                PRIMARY KEY(`id`)
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sends_accountId` ON `pending_sends`(`accountId`)")
    }
}

val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // EmailFtsEntity was added — create the FTS virtual table for local search.
        // IF NOT EXISTS is idempotent for users whose MIGRATION_14_15 (after merge) already created it.
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `emails_fts` USING FTS4(
                content=`emails`,
                `subject` TEXT,
                `body` TEXT,
                `fromName` TEXT,
                `fromEmail` TEXT,
                `toEmail` TEXT,
                `snippet` TEXT
            )
        """)
        db.execSQL("""
            INSERT INTO `emails_fts`(`docid`, `subject`, `body`, `fromName`, `fromEmail`, `toEmail`, `snippet`)
            SELECT `rowid`, `subject`, `body`, `fromName`, `fromEmail`, `toEmail`, `snippet` FROM `emails`
        """)
    }
}

val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE threads ADD COLUMN inDrafts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE emails ADD COLUMN inDrafts INTEGER NOT NULL DEFAULT 0")
    }
}
