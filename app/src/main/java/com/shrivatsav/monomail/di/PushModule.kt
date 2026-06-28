package com.shrivatsav.monomail.di

import com.shrivatsav.monomail.push.PushNotificationManager
import com.shrivatsav.monomail.push.PushNotificationManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @Singleton
    abstract fun bindPushNotificationManager(
        impl: PushNotificationManagerImpl
    ): PushNotificationManager
}
