package com.yansproject.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureWhatsAppShareUtilTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun test01_validIndonesianNumberFormatting() {
        // Leading 0
        val res1 = SecureWhatsAppShareUtil.validatePhoneNumber("0812-3456-7890")
        assertTrue(res1.isValid)
        assertEquals("6281234567890", res1.normalizedNumber)
        assertEquals("62", res1.countryCode)
        assertNull(res1.userErrorMessage)

        // Leading 62 with symbols
        val res2 = SecureWhatsAppShareUtil.validatePhoneNumber("+62 877-7739-8813")
        assertTrue(res2.isValid)
        assertEquals("6287777398813", res2.normalizedNumber)
        assertEquals("62", res2.countryCode)

        // Leading 8 (missing 0)
        val res3 = SecureWhatsAppShareUtil.validatePhoneNumber("85612345678")
        assertTrue(res3.isValid)
        assertEquals("6285612345678", res3.normalizedNumber)
    }

    @Test
    fun test02_internationalCountryCodesValid() {
        // Malaysia (+60)
        val resMY = SecureWhatsAppShareUtil.validatePhoneNumber("+60 12-345 6789")
        assertTrue(resMY.isValid)
        assertEquals("60123456789", resMY.normalizedNumber)
        assertEquals("60", resMY.countryCode)

        // Singapore (+65)
        val resSG = SecureWhatsAppShareUtil.validatePhoneNumber("+65 9123 4567")
        assertTrue(resSG.isValid)
        assertEquals("6591234567", resSG.normalizedNumber)
        assertEquals("65", resSG.countryCode)

        // US (+1)
        val resUS = SecureWhatsAppShareUtil.validatePhoneNumber("+1 (555) 234-5678")
        assertTrue(resUS.isValid)
        assertEquals("15552345678", resUS.normalizedNumber)
        assertEquals("1", resUS.countryCode)
    }

    @Test
    fun test03_invalidAndEmptyPhoneHandling() {
        // Blank
        val resBlank = SecureWhatsAppShareUtil.validatePhoneNumber("")
        assertFalse(resBlank.isValid)
        assertNotNull(resBlank.userErrorMessage)

        // Null
        val resNull = SecureWhatsAppShareUtil.validatePhoneNumber(null)
        assertFalse(resNull.isValid)
        assertNotNull(resNull.userErrorMessage)

        // Only symbols
        val resSymbols = SecureWhatsAppShareUtil.validatePhoneNumber("---+++()")
        assertFalse(resSymbols.isValid)
        assertNotNull(resSymbols.userErrorMessage)

        // Too short
        val resShort = SecureWhatsAppShareUtil.validatePhoneNumber("08123")
        assertFalse(resShort.isValid)
        assertTrue(resShort.userErrorMessage?.contains("digit") == true)

        // Too long (> 15 digits)
        val resLong = SecureWhatsAppShareUtil.validatePhoneNumber("081234567890123456789")
        assertFalse(resLong.isValid)
    }

    @Test
    fun test04_packageVerificationCheck() {
        // Unverified package should be rejected immediately by security rules
        assertFalse(SecureWhatsAppShareUtil.isPackageInstalled(context, "com.untrusted.malware"))
        assertFalse(SecureWhatsAppShareUtil.isPackageInstalled(context, "org.telegram.messenger"))

        // Standard WA package constant is recognized
        assertTrue(SecureWhatsAppShareUtil.VERIFIED_PACKAGES.contains(SecureWhatsAppShareUtil.PACKAGE_WHATSAPP_STANDARD))
        assertTrue(SecureWhatsAppShareUtil.VERIFIED_PACKAGES.contains(SecureWhatsAppShareUtil.PACKAGE_WHATSAPP_BUSINESS))
    }

    @Test
    fun test05_errorEncapsulationNoLeakage() {
        // Invalid recipient phone test for shareDocument
        val tempFile = File(context.cacheDir, "test_invoice.pdf").apply {
            writeText("dummy content")
        }

        val result = SecureWhatsAppShareUtil.shareDocumentToWhatsApp(
            context = context,
            file = tempFile,
            recipientPhone = "invalid_phone_123",
            captionText = "Invoice details"
        )

        assertTrue(result is SecureShareResult.Failure)
        val failure = result as SecureShareResult.Failure
        // Error message must be generic and polite, without stack traces or path leakage
        assertFalse(failure.genericUserMessage.contains("test_invoice.pdf"))
        assertFalse(failure.genericUserMessage.contains("cacheDir"))
        assertFalse(failure.genericUserMessage.contains("Exception"))
        assertTrue(failure.genericUserMessage.isNotEmpty())

        tempFile.delete()
    }
}
