package com.shrivatsav.monomail.di

import com.shrivatsav.monomail.push.PushNotificationManager
import com.shrivatsav.monomail.push.PushNotificationManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("S6517")
@Module
@InstallIn(SingletonComponent::class)
interface PushModule {
    @Binds
    @Singleton
    fun bindPushNotificationManager(
        impl: PushNotificationManagerImpl
    ): PushNotificationManager
}
