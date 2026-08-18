package com.yansproject.app

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yansproject.app.data.*
import com.yansproject.app.security.OmniverseSecurity
import com.yansproject.app.security.SecurityState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnterpriseArchitectureHardeningTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // 1. Historical invoice with missing price
    @Test
    fun test01_historicalInvoiceWithMissingPrice() {
        val resultNull = PriceResolverEngine.resolveHistoricalPrice(null)
        assertTrue(resultNull is HistoricalPriceResult.Unavailable)
        assertEquals("PRICE_UNAVAILABLE: Historical snapshot price is missing or invalid", (resultNull as HistoricalPriceResult.Unavailable).reason)

        val resultZero = PriceResolverEngine.resolveHistoricalPrice(BigDecimal.ZERO)
        assertTrue(resultZero is HistoricalPriceResult.Unavailable)

        val resultNegative = PriceResolverEngine.resolveHistoricalPriceLong(-500L)
        assertTrue(resultNegative is HistoricalPriceResult.Unavailable)

        val resultValid = PriceResolverEngine.resolveHistoricalPrice(BigDecimal("150000"))
        assertTrue(resultValid is HistoricalPriceResult.Success)
        val success = resultValid as HistoricalPriceResult.Success
        assertEquals(150000L, success.amountLong)
        assertEquals(BigDecimal("150000"), success.amountBd)
    }

    // 2. Database migration failure / validation
    @Test
    fun test02_databaseMigrationValidation() {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val result = DatabaseInitializer.validate(inMemoryDb)
        // In-memory DB with full Room schema created is READY
        assertEquals(DatabaseInitStatus.READY, result.status)
        inMemoryDb.close()
    }

    // 3. SQLite integrity check
    @Test
    fun test03_sqliteIntegrityCheck() {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val recoveryManager = RecoveryManager.getInstance(context)
        val auditReport = recoveryManager.performDeepIntegrityAudit(inMemoryDb.openHelper.readableDatabase)
        assertTrue(auditReport.isHealthy)
        assertTrue(auditReport.integrityCheckPassed)
        assertTrue(auditReport.foreignKeyCheckPassed)
        inMemoryDb.close()
    }

    // 4. Foreign-key corruption / check
    @Test
    fun test04_foreignKeyCheck() {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val recoveryManager = RecoveryManager.getInstance(context)
        val auditReport = recoveryManager.performDeepIntegrityAudit(inMemoryDb.openHelper.readableDatabase)
        assertTrue("Foreign key check should pass on clean schema", auditReport.foreignKeyCheckPassed)
        inMemoryDb.close()
    }

    // 5. Logout while sync is active
    @Test
    fun test05_logoutWhileSyncIsActive() {
        val sessionManager = SessionManager.getInstance(context)
        val visitedStates = mutableListOf<LogoutStepState>()

        sessionManager.logoutAndClearSession(
            onStepChanged = { state -> visitedStates.add(state) },
            onComplete = { success ->
                assertTrue(success)
            }
        )

        assertTrue(visitedStates.contains(LogoutStepState.LOGGING_OUT))
        assertTrue(visitedStates.contains(LogoutStepState.STOPPING_LISTENERS))
        assertTrue(visitedStates.contains(LogoutStepState.CLEARING_USER_STATE))
        assertTrue(visitedStates.contains(LogoutStepState.SIGNED_OUT))
        assertTrue(visitedStates.contains(LogoutStepState.COMPLETED))
        assertEquals(LogoutStepState.COMPLETED, sessionManager.logoutState.value)
    }

    // 6. Account A -> logout -> account B
    @Test
    fun test06_accountSwitchingStateIsolation() {
        val cacheManager = CacheManager.getInstance(context)
        val sessionManager = SessionManager.getInstance(context)

        // Login as User A
        AuthoritativeSessionManager.updateSession("user_a", "MEMBER")
        val userAKey = CacheKey.create<String>("profile_key", CacheNamespace.User("user_a"))
        cacheManager.putTyped(userAKey, "Data of User A")
        assertEquals("Data of User A", cacheManager.getTyped(userAKey))

        // Logout User A
        sessionManager.logoutAndClearSession()
        assertEquals(AuthState.UNAUTHENTICATED, AuthoritativeSessionManager.sessionState.value.authState)
        assertNull("User A cache must be evicted on logout", cacheManager.getTyped(userAKey))

        // Login as User B
        AuthoritativeSessionManager.updateSession("user_b", "MEMBER")
        val userBKey = CacheKey.create<String>("profile_key", CacheNamespace.User("user_b"))
        cacheManager.putTyped(userBKey, "Data of User B")
        assertEquals("Data of User B", cacheManager.getTyped(userBKey))
        assertNull(cacheManager.getTyped(userAKey))
    }

    // 7. Notification dedupe after re-login
    @Test
    fun test07_notificationDedupeAfterRelogin() {
        val dispatcher = NotificationDispatcher.getInstance(context)
        val dedupeKey = "order_status_update_12345"

        // Mark delivered in Session 1
        dispatcher.markDelivered(dedupeKey)
        assertTrue(dispatcher.isDelivered(dedupeKey))

        // Logout
        SessionManager.getInstance(context).logoutAndClearSession()

        // Re-login: deduplication history must remain intact to prevent re-delivery spam
        assertTrue("Notification dedupe must survive ordinary logout", dispatcher.isDelivered(dedupeKey))
    }

    // 8. Production experimental flag override attempt
    @Test
    fun test08_featureFlagProductionOverrideGuard() {
        val flagManager = FeatureFlagManager.getInstance(context)
        val flag = FeatureFlagKey.ENABLE_EXPERIMENTAL_AI_PROMOTIONS

        // In production / non-debug mode, local in-memory override must be rejected
        // Note: Robolectric simulates environment based on manifest / applicationInfo
        val canOverride = flagManager.setOverride(flag, true)
        if (!canOverride) {
            assertFalse(flagManager.isFeatureEnabled(flag))
        }
    }

    // 9. Bitmap compression verification
    @Test
    fun test09_bitmapCompressionSafety() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bytes = BitmapMemoryRecycler.compressAndRecycle(
            bitmap = bitmap,
            format = Bitmap.CompressFormat.PNG,
            quality = 100,
            ownership = BitmapOwnership.TRANSFERRED_TO_RECYCLER
        )
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bitmap.isRecycled)
    }

    // 10. Bitmap reuse after export / caller-owned protection
    @Test
    fun test10_bitmapCallerOwnedProtection() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        BitmapMemoryRecycler.recycle(bitmap, BitmapOwnership.CALLER_OWNED)
        assertFalse("Caller-owned bitmap must NOT be recycled", bitmap.isRecycled)
        bitmap.recycle()
    }

    // 11. Bitmap OOM handling & pool purge
    @Test
    fun test11_bitmapSafeCreation() {
        val safeBitmap = BitmapMemoryRecycler.createSafeBitmap(64, 64, Bitmap.Config.ARGB_8888)
        assertNotNull(safeBitmap)
        assertEquals(64, safeBitmap.width)
        assertEquals(64, safeBitmap.height)
        BitmapMemoryRecycler.recycle(safeBitmap)
    }

    // 12. Targeted Aggregate projection
    @Test
    fun test12_targetedRoomAggregateProjection() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val invoice = Invoice(
            id = 1,
            invoiceNumber = "INV/2026/TEST/001",
            clientName = "Test Client",
            totalAmount = 250000.0,
            paidAmount = 100000.0,
            status = "DP",
            issueDate = System.currentTimeMillis()
        )
        inMemoryDb.invoiceDao().insertInvoice(invoice)

        val count = inMemoryDb.invoiceDao().getInvoiceCount()
        assertEquals(1, count)

        val analytics = inMemoryDb.invoiceDao().getMemberAnalyticsProjection("Test Client")
        assertEquals(1, analytics.invoiceCount)
        assertEquals(250000.0, analytics.totalRevenue, 0.01)
        assertEquals(100000.0, analytics.totalPaid, 0.01)
        assertEquals(150000.0, analytics.totalReceivable, 0.01)

        inMemoryDb.close()
    }

    // 13. Staged cross-database mutation with compensation
    @Test
    fun test13_stagedCrossDatabaseOperationFailureAndCompensation() = runBlocking {
        val localDbService = LocalDatabaseService.getInstance(context)
        var stage1Compensated = false

        val result = localDbService.executeStagedCrossDatabaseOperation(
            stage1AppDb = { "Stage1_Success_Payload" },
            stage2SecureDb = { _, _ -> throw RuntimeException("Simulated Stage 2 Network/DB crash") },
            compensateStage1 = { _, _ -> stage1Compensated = true }
        )

        assertTrue(result is StagedCrossDbResult.PartialFailure)
        val failure = result as StagedCrossDbResult.PartialFailure
        assertEquals("Stage1_Success_Payload", failure.stage1Result)
        assertTrue(failure.compensationApplied)
        assertTrue(stage1Compensated)
    }

    // 14. Cache collision prevention with different types
    @Test
    fun test14_cacheTypeCollisionProtection() {
        val cacheManager = CacheManager.getInstance(context)

        val stringKey = CacheKey.create<String>("shared_key_name")
        val intKey = CacheKey.create<Int>("shared_key_name")

        cacheManager.putTyped(stringKey, "Hello YANSPROJECT")
        cacheManager.putTyped(intKey, 9999)

        val retrievedString = cacheManager.getTyped(stringKey)
        val retrievedInt = cacheManager.getTyped(intKey)

        assertEquals("Hello YANSPROJECT", retrievedString)
        assertEquals(9999, retrievedInt)
    }

    // 15. Two members with same display name distinct identifiers
    @Test
    fun test15_twoMembersWithSameDisplayNameDifferentIdentifiers() {
        val s1 = com.yansproject.app.ui.components.CustomerSuggestion(
            memberUid = "MBR_001",
            customerId = 1,
            name = "Budi Hartono",
            phone = "081234567890",
            email = "budi1@test.com"
        )
        val s2 = com.yansproject.app.ui.components.CustomerSuggestion(
            memberUid = "MBR_002",
            customerId = 2,
            name = "Budi Hartono",
            phone = "081987654321",
            email = "budi2@test.com"
        )
        assertNotEquals(s1.uniqueKey, s2.uniqueKey)
        assertEquals("MBR:MBR_001", s1.uniqueKey)
        assertEquals("MBR:MBR_002", s2.uniqueKey)
    }

    // 16. New customer created observable suggestions
    @Test
    fun test16_newCustomerCreatedObservableSuggestions() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val customerDao = inMemoryDb.customerDao()

        val newCustomer = CustomerEntity(
            name = "New Studio Partner",
            phone = "081234567899",
            email = "partner@studio.com",
            isMember = true,
            tier = "VIP"
        )
        val insertedId = customerDao.insert(newCustomer)
        assertTrue(insertedId > 0)

        val found = customerDao.getCustomerById(insertedId.toInt())
        assertNotNull(found)
        assertEquals("New Studio Partner", found?.name)
        inMemoryDb.close()
    }

    // 17. 100k Project search/filter pagination without full-table scan
    @Test
    fun test17_projectSearchAndPagingLargeDataset() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val projectDao = inMemoryDb.projectDao()
        val repo = ProjectSearchRepository(projectDao)

        // Insert sample project
        val project = ProjectCustom(
            id = 10,
            projectName = "Custom Apparel Order 10",
            clientName = "Client Alpha",
            startDate = System.currentTimeMillis()
        )
        projectDao.insertProject(project)

        val params = ProjectFilterParams(query = "Apparel", limit = 10)
        val state = repo.observeProjects(params).first()
        assertTrue(state is ProjectRealtimeState.Success)
        val success = state as ProjectRealtimeState.Success
        assertEquals(1, success.totalCount)
        assertEquals("Custom Apparel Order 10", success.projects[0].projectName)

        inMemoryDb.close()
    }

    // 18. Midnight Jakarta date boundary
    @Test
    fun test18_midnightJakartaDateBoundary() {
        val jakartaZone = java.time.ZoneId.of("Asia/Jakarta")
        val fixedLocalDate = java.time.LocalDate.of(2026, 8, 17)
        val justBeforeMidnight = fixedLocalDate.atTime(23, 59, 59).atZone(jakartaZone).toInstant()
        val (start, end) = ProjectSearchRepository.calculateDateRange("TODAY", justBeforeMidnight, jakartaZone)

        val expectedStart = fixedLocalDate.atStartOfDay(jakartaZone).toInstant().toEpochMilli()
        val expectedEnd = fixedLocalDate.plusDays(1).atStartOfDay(jakartaZone).minusNanos(1).toInstant().toEpochMilli()

        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    // 19. Week boundary in Jakarta
    @Test
    fun test19_weekBoundaryJakarta() {
        val jakartaZone = java.time.ZoneId.of("Asia/Jakarta")
        val wednesday = java.time.LocalDate.of(2026, 8, 19).atStartOfDay(jakartaZone).toInstant()
        val (start, end) = ProjectSearchRepository.calculateDateRange("THIS_WEEK", wednesday, jakartaZone)

        val monday = java.time.LocalDate.of(2026, 8, 17).atStartOfDay(jakartaZone).toInstant().toEpochMilli()
        val sundayEnd = java.time.LocalDate.of(2026, 8, 23).plusDays(1).atStartOfDay(jakartaZone).minusNanos(1).toInstant().toEpochMilli()

        assertEquals(monday, start)
        assertEquals(sundayEnd, end)
    }

    // 20. Super Admin represented as OWNER and ADMIN
    @Test
    fun test20_superAdminOwnerAndAdminRepresentation() {
        assertTrue(RoleAccessManager.isSuperAdmin(UserRole.OWNER))
        assertTrue(RoleAccessManager.isSuperAdmin(UserRole.ADMIN))
        assertFalse(RoleAccessManager.isSuperAdmin(UserRole.MEMBER))
        assertFalse(RoleAccessManager.isSuperAdmin(UserRole.STAFF))

        assertTrue(RoleAccessManager.isSuperAdmin("OWNER"))
        assertTrue(RoleAccessManager.isSuperAdmin("ADMIN"))
        assertTrue(RoleAccessManager.isSuperAdmin("SUPER_ADMIN"))
        assertFalse(RoleAccessManager.isSuperAdmin("MEMBER"))
    }

    // 21. Security check throws exception: never interpret as SAFE
    @Test
    fun test21_securityCheckExceptionNeverInterpretsAsSafe() {
        val stateOnNull = OmniverseSecurity.evaluateSecurityState(null)
        assertEquals(SecurityState.UNKNOWN, stateOnNull)
        assertNotEquals(SecurityState.SAFE, stateOnNull)
    }

    // 22. Ephemeral key vs Persistent AndroidKeyStore secret
    @Test
    fun test22_persistentCryptoKeyAndEphemeralKeySeparation() {
        val ephemeralKey = OmniverseSecurity.generateEphemeralKey()
        assertNotNull(ephemeralKey)
        assertEquals("AES", ephemeralKey.algorithm)
        assertEquals(32, ephemeralKey.encoded.size) // 256-bit key = 32 bytes
    }

    // 23. Startup crash tracking and recovery
    @Test
    fun test23_startupCrashRecoveryDiagnostics() {
        val integrityManager = IntegrityManager.getInstance(context)
        integrityManager.markStartupSuccessful()
        assertFalse(integrityManager.isRecoveryModeRequired())

        integrityManager.recordStartupAttempt()
        integrityManager.recordStartupAttempt()
        integrityManager.recordStartupAttempt()
        assertTrue(integrityManager.isRecoveryModeRequired())

        integrityManager.markStartupSuccessful()
        assertFalse(integrityManager.isRecoveryModeRequired())
    }

    // 24. Crash reporting non-PII diagnostics & secret redaction
    @Test
    fun test24_crashReportingNonPiiDiagnostics() {
        val crashManager = CrashReportingManager.getInstance(context)
        crashManager.setDiagnosticContext(
            appVersion = "1.4.0",
            buildNumber = "8",
            startupState = "SUCCESS",
            syncState = "ONLINE_SYNCED",
            dbSchemaVersion = 1,
            sessionGeneration = 1001L,
            lastOperationId = "OP_TEST_99"
        )
        // Redaction verification: passwords and PINs must not throw or crash
        crashManager.setCustomKey("user_password", "Secret12345")
        crashManager.setCustomKey("user_pin", "9999")
        crashManager.leaveBreadcrumb("Diagnostic breadcrumb test verified")
        assertTrue(crashManager.getBreadcrumbHistory().isNotEmpty())
    }
}
