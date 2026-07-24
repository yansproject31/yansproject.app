package com.yansproject.app.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

/**
 * SecurityShieldDiModule: High-Security and High-Precision dependency injection registry.
 * Disseminates the secure network shield client and specialized accounting handlers across all services.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityShieldDiModule {

    /**
     * Injects the standard secure OkHttpClient equipped with active SSL Certificate Pinning.
     */
    @Provides
    @Singleton
    fun provideSecureOkHttpClient(): OkHttpClient {
        return NetworkSecurityShield.getSecureOkHttpClient()
    }

    /**
     * Injects a specialized named qualifier for precision financial-only network communication pipelines.
     */
    @Provides
    @Singleton
    @Named("FinancialClient")
    fun provideFinancialOkHttpClient(): OkHttpClient {
        return NetworkSecurityShield.getSecureOkHttpClient()
    }

    /**
     * Injects the localized precise rupiah monetary calculations engine.
     */
    @Provides
    @Singleton
    fun provideIdrAccountingEngine(): IdrAccountingEngine {
        return IdrAccountingEngine
    }

    /**
     * Injects the security tamper detection module.
     */
    @Provides
    @Singleton
    fun provideNetworkSecurityShield(): NetworkSecurityShield {
        return NetworkSecurityShield
    }

    /**
     * Injects the database drift repair guard to guarantee structure safety.
     */
    @Provides
    @Singleton
    fun provideSchemaDriftRepairGuard(): SchemaDriftRepairGuard {
        return SchemaDriftRepairGuard
    }
}
