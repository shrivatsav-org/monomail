package com.shrivatsav.monomail.di

import android.content.Context
import com.shrivatsav.monomail.data.local.AppDatabase
import com.shrivatsav.monomail.data.local.EmailDao
import com.shrivatsav.monomail.data.local.PendingActionDao
import com.shrivatsav.monomail.data.local.ScheduledMessageDao
import com.shrivatsav.monomail.data.local.ThreadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides fun provideThreadDao(db: AppDatabase): ThreadDao = db.threadDao()
    @Provides fun provideEmailDao(db: AppDatabase): EmailDao = db.emailDao()
    @Provides fun provideScheduledMessageDao(db: AppDatabase): ScheduledMessageDao = db.scheduledMessageDao()
    @Provides fun providePendingActionDao(db: AppDatabase): PendingActionDao = db.pendingActionDao()
}
