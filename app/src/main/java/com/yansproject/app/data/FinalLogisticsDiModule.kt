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
object FinalLogisticsDiModule {

    @Provides
    @Singleton
    fun provideShippingLabelCompiler(): ShippingLabelCompiler {
        return ShippingLabelCompiler()
    }

    @Provides
    @Singleton
    fun provideDataConflictResolver(@ApplicationContext context: Context): DataConflictResolver {
        return DataConflictResolver(context)
    }

    @Provides
    @Singleton
    fun provideDebtReminderManager(@ApplicationContext context: Context): DebtReminderManager {
        return DebtReminderManager(context)
    }
}
