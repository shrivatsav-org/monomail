package com.shrivatsav.monomail.di

import android.content.Context
import com.shrivatsav.monomail.data.pgp.PgpKeyManager
import com.shrivatsav.monomail.data.pgp.PgpKeyStorage
import com.shrivatsav.monomail.data.pgp.PgpManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PgpModule {

    @Provides @Singleton
    fun providePgpKeyStorage(
        @ApplicationContext context: Context
    ): PgpKeyStorage = PgpKeyStorage(context)

    @Provides @Singleton
    fun providePgpKeyManager(
        storage: PgpKeyStorage
    ): PgpKeyManager = PgpKeyManager(storage)

    @Provides @Singleton
    fun providePgpManager(
        keyManager: PgpKeyManager,
        storage: PgpKeyStorage
    ): PgpManager = PgpManager(keyManager, storage)
}
