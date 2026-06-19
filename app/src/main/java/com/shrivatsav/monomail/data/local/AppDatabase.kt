package com.shrivatsav.monomail.data.local
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shrivatsav.monomail.security.SecurityUtil
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
@Database(
    entities = [ThreadEntity::class, EmailEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun emailDao(): EmailDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = String(SecurityUtil.getDatabasePassphrase(context)).toByteArray()
                val factory = SupportOpenHelperFactory(passphrase)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monomail_database"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_2_3, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
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
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE emails ADD COLUMN ccEmail TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE emails ADD COLUMN bccEmail TEXT NOT NULL DEFAULT ''")
    }
}
