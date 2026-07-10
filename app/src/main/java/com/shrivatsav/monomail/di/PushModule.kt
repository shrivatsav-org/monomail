package com.shrivatsav.monomail.di

import com.shrivatsav.monomail.push.PushNotificationManager
import com.shrivatsav.monomail.push.PushNotificationManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PushModule {
    @Provides
    @Singleton
    fun providePushNotificationManager(
        impl: PushNotificationManagerImpl
    ): PushNotificationManager = impl
}
