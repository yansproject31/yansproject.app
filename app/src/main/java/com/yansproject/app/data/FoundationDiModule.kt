package com.yansproject.app.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FoundationDiModule {

    @Provides
    @Singleton
    fun provideYansRoomDatabase(@ApplicationContext context: Context): YansRoomDatabase {
        return YansRoomDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideOfflineActionDao(database: YansRoomDatabase): OfflineActionDao {
        return database.offlineActionDao()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(@ApplicationContext context: Context): com.google.firebase.firestore.FirebaseFirestore {
        return AppModule.provideFirestore(context)
    }

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor {
        return NetworkMonitor(context)
    }

    @Provides
    @Singleton
    fun provideSystemCleaner(
        @ApplicationContext context: Context,
        offlineActionDao: OfflineActionDao,
        appDatabase: AppDatabase
    ): SystemCleaner {
        return SystemCleaner(context, offlineActionDao, appDatabase)
    }
}
