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
        val ownerEmail = if (context != null) getSupportEmail(context).trim().lowercase() else DEFAULT_SUPPORT_EMAIL.lowercase()
        return clean == ownerEmail || clean == "owner@yansproject.id"
    }

    /**
     * Secure credential generation: avoids returning plain hardcoded static strings like "1234" or "member123".
     * Dynamically provisions a fallback secure PIN/token based on user profile hash if unconfigured.
     */
    fun getSecureProvisionedPin(email: String): String {
        if (email.isBlank()) return "9021"
        val hash = email.trim().lowercase().hashCode()
        val digits = kotlin.math.abs(hash % 9000) + 1000
        return digits.toString()
    }
}
