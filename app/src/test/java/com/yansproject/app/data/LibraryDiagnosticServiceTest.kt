package com.yansproject.app.data

import com.yansproject.app.data.diagnostics.DiagnosticSeverity
import com.yansproject.app.data.diagnostics.LibraryDiagnosticService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LibraryDiagnosticServiceTest {

    @Before
    fun setUp() {
        LibraryDiagnosticService.clearLogs()
    }

    @Test
    fun testDiagnosticLoggingAndRetrieval() {
        LibraryDiagnosticService.log(
            targetPath = "library/test.json",
            operation = "TEST_OP",
            severity = DiagnosticSeverity.INFO,
            message = "Test message for diagnostics"
        )

        val logs = LibraryDiagnosticService.diagnosticLogs.value
        assertEquals(1, logs.size)
        assertEquals("library/test.json", logs[0].targetPath)
        assertEquals(DiagnosticSeverity.INFO, logs[0].severity)
        assertEquals("Test message for diagnostics", logs[0].message)
    }

    @Test
    fun testSafeJsonParsingWithValidJson() {
        val validJson = """{"id": "test_book", "title": "Test Title"}"""
        val parsed = LibraryDiagnosticService.safeParseJson(validJson, "library/test.json")

        assertNotNull(parsed)
        assertEquals("test_book", parsed?.getString("id"))
        assertEquals("Test Title", parsed?.getString("title"))

        val logs = LibraryDiagnosticService.diagnosticLogs.value
        assertTrue(logs.any { it.severity == DiagnosticSeverity.SUCCESS })
    }

    @Test
    fun testSafeJsonParsingWithMalformedJsonDoesNotCrash() {
        // Malformed JSON missing quotes, bracket mismatch
        val malformedJson = """{ id: test_book, title: "Unclosed string """
        val parsed = LibraryDiagnosticService.safeParseJson(malformedJson, "library/corrupt.json")

        // Must return null without throwing JSONException or crashing
        assertNull(parsed)

        val logs = LibraryDiagnosticService.diagnosticLogs.value
        assertTrue("Must log an error for malformed JSON", logs.any { it.severity == DiagnosticSeverity.ERROR })
    }

    @Test
    fun testSafeJsonParsingWithEmptyString() {
        val parsed = LibraryDiagnosticService.safeParseJson("", "library/empty.json")
        assertNull(parsed)

        val logs = LibraryDiagnosticService.diagnosticLogs.value
        assertTrue(logs.any { it.severity == DiagnosticSeverity.WARNING })
    }

    @Test
    fun testExportLogsAsText() {
        LibraryDiagnosticService.log(
            targetPath = "library/manifest.json",
            operation = "MANIFEST_READ",
            severity = DiagnosticSeverity.SUCCESS,
            message = "Manifest verified"
        )

        val exportText = LibraryDiagnosticService.exportLogsAsText()
        assertTrue(exportText.contains("YANSPROJECT.ID LIBRARY ASSETS DIAGNOSTIC LOGS"))
        assertTrue(exportText.contains("library/manifest.json"))
        assertTrue(exportText.contains("Manifest verified"))
    }
}
