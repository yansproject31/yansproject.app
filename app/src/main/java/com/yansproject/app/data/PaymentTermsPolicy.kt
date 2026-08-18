package com.yansproject.app.data

object PaymentTermsPolicy {
    const val DEFAULT_PAYMENT_TERMS_DAYS = 3

    fun calculateDueDate(issueDate: Long = System.currentTimeMillis(), customDays: Int? = null): Long {
        val days = customDays ?: DEFAULT_PAYMENT_TERMS_DAYS
        return issueDate + (days * 86400000L)
    }
}
