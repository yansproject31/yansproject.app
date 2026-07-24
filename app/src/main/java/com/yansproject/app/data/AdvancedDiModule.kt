package com.yansproject.app.data

import android.content.Context
import com.yansproject.app.ui.ExportShareManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdvancedDiModule {

    @Provides
    @Singleton
    fun provideCryptoSecurityGuard(): CryptoSecurityGuard {
        return CryptoSecurityGuard()
    }

    @Provides
    @Singleton
    fun provideLocalReportExporter(@ApplicationContext context: Context): LocalReportExporter {
        return LocalReportExporter(context)
    }

    @Provides
    @Singleton
    fun provideExportShareManager(@ApplicationContext context: Context): ExportShareManager {
        return ExportShareManager(context)
    }
}
