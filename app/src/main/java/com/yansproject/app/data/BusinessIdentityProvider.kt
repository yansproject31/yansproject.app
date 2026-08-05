package com.yansproject.app.data

import android.content.Context
import com.yansproject.app.ui.AppSettings

/**
 * BusinessIdentityProvider: Centralized configuration service for company identity,
 * support contact info, default addresses, and secure credential provisioning.
 */
object BusinessIdentityProvider {

    const val DEFAULT_COMPANY_NAME = "YANSPROJECT.ID"
    const val DEFAULT_SUPPORT_EMAIL = "yansart31@gmail.com"
    const val DEFAULT_SUPPORT_WHATSAPP = "+62 877-7739-8813"
    const val DEFAULT_STORE_ADDRESS = "Tangerang, Banten"
    const val DEFAULT_STORE_TAGLINE = "Luxury Visual Identity & Custom Merch"
    const val DEFAULT_BANK_NAME = "BRI"
    const val DEFAULT_ACCOUNT_NUMBER = "736901039928537"
    const val DEFAULT_ACCOUNT_HOLDER = "ACHMAD ROBBIYANSYAH"

    fun getCompanyName(context: Context): String {
        val configured = AppSettings.getStoreName(context)
        return if (configured.isNotBlank()) configured else DEFAULT_COMPANY_NAME
    }

    fun getSupportEmail(context: Context): String {
        val configured = AppSettings.getEmail(context)
        return if (configured.isNotBlank()) configured else DEFAULT_SUPPORT_EMAIL
    }

    fun getSupportWhatsApp(context: Context): String {
        val configured = AppSettings.getWhatsApp(context)
        return if (configured.isNotBlank()) configured else DEFAULT_SUPPORT_WHATSAPP
    }

    fun getStoreAddress(context: Context): String {
        val configured = AppSettings.getAddress(context)
        return if (configured.isNotBlank()) configured else DEFAULT_STORE_ADDRESS
    }

    fun isOwnerEmail(email: String, context: Context? = null): Boolean {
        val clean = email.trim().lowercase()
        if (clean.isEmpty()) return false
        val ownerEmail = if (context != null) getSupportEmail(context).trim().lowercase() else DEFAULT_SUPPORT_EMAIL.lowercase()
        return clean == ownerEmail || clean == DEFAULT_SUPPORT_EMAIL.lowercase() || clean == "admin@yansproject.id"
    }

    /**
     * Secure credential provisioning: retrieves configured PIN from encrypted local storage.
     * Returns null if unconfigured, requiring explicit registration or owner provisioning.
     */
    fun getSecureProvisionedPin(email: String, context: Context): String? {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return null
        val credential = AppSettings.getLocalUserCredential(context, cleanEmail)
        if (credential == null) {
            android.util.Log.d("BusinessIdentityProvider", "No provisioned credential found for: $cleanEmail")
        }
        return credential?.passwordOrPin
    }
}
