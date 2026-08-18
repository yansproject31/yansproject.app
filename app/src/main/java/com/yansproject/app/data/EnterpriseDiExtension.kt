package com.yansproject.app.data

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EnterpriseDiExtension {

    @Provides
    @Singleton
    fun provideLocalEncryptedBackupManager(@ApplicationContext context: Context): LocalEncryptedBackupManager {
        return LocalEncryptedBackupManager(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
