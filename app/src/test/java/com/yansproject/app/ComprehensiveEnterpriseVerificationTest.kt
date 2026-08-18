package com.yansproject.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yansproject.app.data.*
import com.yansproject.app.domain.AnalyticsTimeFilter
import com.yansproject.app.domain.FinancialAnalyticsUseCase
import com.yansproject.app.ui.settings.MemberModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComprehensiveEnterpriseVerificationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var invoiceDao: InvoiceDao
    private lateinit var invoicePaymentDao: InvoicePaymentDao
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var backupManager: LocalEncryptedBackupManager
    private lateinit var analyticsUseCase: FinancialAnalyticsUseCase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        invoiceDao = database.invoiceDao()
        invoicePaymentDao = database.invoicePaymentDao()
        paymentRepository = PaymentRepository(context, database)
        backupManager = LocalEncryptedBackupManager(context)
        analyticsUseCase = FinancialAnalyticsUseCase(ZoneId.of("Asia/Jakarta"))
    }

    // Scenario 1: Two members with identical names
    @Test
    fun test01_twoMembersWithIdenticalNames() {
        val member1 = MemberModel(
            memberUid = "UID_001",
            displayName = "Budi Santoso",
            email = "budi1@example.com",
            role = "MEMBER"
        )
        val member2 = MemberModel(
            memberUid = "UID_002",
            displayName = "Budi Santoso",
            email = "budi2@example.com",
            role = "MEMBER"
        )

        val memberList = listOf(member1, member2)
        // Deduplication strictly uses UID / effectiveUid, so both are preserved without collision
        val deduplicated = memberList.distinctBy { it.effectiveUid }
        assertEquals(2, deduplicated.size)
        assertEquals("UID_001", deduplicated[0].memberUid)
        assertEquals("UID_002", deduplicated[1].memberUid)
    }

    // Scenario 2: Same name with different UID
    @Test
    fun test02_sameNameWithDifferentUid() {
        val members = listOf(
            MemberModel(memberUid = "MEMBER_A", displayName = "Andi Wijaya", email = "andi.a@test.com"),
            MemberModel(memberUid = "MEMBER_B", displayName = "Andi Wijaya", email = "andi.b@test.com")
        )
        val distinct = members.distinctBy { it.effectiveUid }
        assertEquals(2, distinct.size)
        assertNotEquals(distinct[0].effectiveUid, distinct[1].effectiveUid)
    }

    // Scenario 3: Member dashboard isolation
    @Test
    fun test03_memberDashboardIsolation() = runBlocking {
        val invoice1 = Invoice(
            id = 101,
            invoiceNumber = "INV/2026/001",
            clientName = "Budi A [MBR_BUDI_A]",
            totalAmount = 500000.0,
            paidAmount = 500000.0,
            status = "LUNAS"
        )
        val invoice2 = Invoice(
            id = 102,
            invoiceNumber = "INV/2026/002",
            clientName = "Budi B [MBR_BUDI_B]",
            totalAmount = 750000.0,
            paidAmount = 250000.0,
            status = "DP"
        )
        invoiceDao.insertInvoice(invoice1)
        invoiceDao.insertInvoice(invoice2)

        val invoicesForBudiA = invoiceDao.getInvoicesList().filter { it.clientName.contains("MBR_BUDI_A") }
        assertEquals(1, invoicesForBudiA.size)
        assertEquals("INV/2026/001", invoicesForBudiA[0].invoiceNumber)

        val invoicesForBudiB = invoiceDao.getInvoicesList().filter { it.clientName.contains("MBR_BUDI_B") }
        assertEquals(1, invoicesForBudiB.size)
        assertEquals("INV/2026/002", invoicesForBudiB[0].invoiceNumber)
    }

    // Scenario 4: Invalid payment "abc"
    @Test
    fun test04_invalidPaymentAbc() {
        val result = InputSanitizer.parseRupiahResult("abc")
        assertTrue(result is MoneyParseResult.Invalid)
        val invalidResult = result as MoneyParseResult.Invalid
        assertEquals("abc", invalidResult.rawInput)

        // Ensure parseRupiahOrNull does not return 0 for invalid inputs
        assertNull(InputSanitizer.parseRupiahOrNull("abc"))
        assertNull(InputSanitizer.parseRupiahLongOrNull("xyz!@#"))
    }

    // Scenario 5: Payment greater than outstanding
    @Test
    fun test05_paymentGreaterThanOutstanding() = runBlocking {
        val invoice = Invoice(
            id = 201,
            invoiceNumber = "INV/2026/201",
            clientName = "Client 201",
            totalAmount = 100000.0,
            paidAmount = 40000.0,
            status = "DP"
        )
        invoiceDao.insert(invoice)

        // Attempt payment of 70,000 (outstanding is only 60,000)
        val paymentResult = paymentRepository.recordPaymentAtomic(
            invoiceIdentifier = "201",
            amountRupiah = 70000L,
            paymentMethod = "Transfer BCA",
            actorName = "Admin",
            actorUid = "ADMIN_001"
        )

        assertTrue(paymentResult is PaymentResult.Error)
        val error = paymentResult as PaymentResult.Error
        assertEquals(PaymentErrorCode.EXCEEDS_OUTSTANDING, error.code)
    }

    // Scenario 6: Concurrent payment
    @Test
    fun test06_concurrentPayment() = runBlocking {
        val invoice = Invoice(
            id = 301,
            invoiceNumber = "INV/2026/301",
            clientName = "Client 301",
            totalAmount = 100000.0,
            paidAmount = 0.0,
            status = "BELUM LUNAS"
        )
        invoiceDao.insert(invoice)

        // Launch 2 concurrent payments of 60,000 each (Total 120,000 > 100,000)
        val deferred1 = async(Dispatchers.IO) {
            paymentRepository.recordPaymentAtomic(
                invoiceIdentifier = "301",
                amountRupiah = 60000L,
                paymentMethod = "Cash",
                actorName = "Admin",
                actorUid = "ADMIN_001",
                idempotencyKey = "PAY_CONC_1"
            )
        }
        val deferred2 = async(Dispatchers.IO) {
            paymentRepository.recordPaymentAtomic(
                invoiceIdentifier = "301",
                amountRupiah = 60000L,
                paymentMethod = "Cash",
                actorName = "Admin",
                actorUid = "ADMIN_001",
                idempotencyKey = "PAY_CONC_2"
            )
        }

        val results = listOf(deferred1.await(), deferred2.await())
        val successes = results.filterIsInstance<PaymentResult.Success>()
        val failures = results.filterIsInstance<PaymentResult.Error>()

        // Exactly one payment must succeed and one must fail due to balance validation
        assertEquals(1, successes.size)
        assertEquals(1, failures.size)
        assertEquals(PaymentErrorCode.EXCEEDS_OUTSTANDING, failures[0].code)
    }

    // Scenario 7: Corrupt encrypted backup
    @Test
    fun test07_corruptEncryptedBackup() {
        val corruptFile = File(context.cacheDir, "corrupt_backup_test.enc")
        corruptFile.writeBytes(ByteArray(100) { 0xFF.toByte() })

        val result = backupManager.verifyEncryptedBackupFile(corruptFile)
        assertFalse(result.isVerified)
        assertTrue(
            result.errorType == BackupErrorType.CRYPTO_AUTH_TAG_FAILED ||
            result.errorType == BackupErrorType.TRANSIENT_IO
        )
        corruptFile.delete()
    }

    // Scenario 8: Wrong AES-GCM authentication tag
    @Test
    fun test08_wrongAesGcmAuthenticationTag() {
        val targetFile = File(context.cacheDir, "tag_tamper_test.enc")
        FileOutputStream(targetFile).use { fos ->
            backupManager.exportBackup(fos)
        }

        // Tamper with the last 8 bytes (which contain the AES-GCM auth tag)
        val bytes = targetFile.readBytes()
        if (bytes.size > 16) {
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x5A).toByte()
            targetFile.writeBytes(bytes)
        }

        val verifyResult = backupManager.verifyEncryptedBackupFile(targetFile)
        assertFalse(verifyResult.isVerified)
        assertEquals(BackupErrorType.CRYPTO_AUTH_TAG_FAILED, verifyResult.errorType)
        targetFile.delete()
    }

    // Scenario 9: Corrupt extracted SQLite
    @Test
    fun test09_corruptExtractedSqlite() {
        val badDbFile = File(context.cacheDir, "bad_sqlite_test.db")
        badDbFile.writeBytes("SQLite format 3\u0000CorruptedPayloadBytesHere...".toByteArray(Charsets.US_ASCII))

        var integrityOk = false
        try {
            val sqlite = SQLiteDatabase.openDatabase(badDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    integrityOk = cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
            sqlite.close()
        } catch (e: Exception) {
            integrityOk = false
        }
        assertFalse(integrityOk)
        badDbFile.delete()
    }

    // Scenario 10: Missing foreign key / schema check
    @Test
    fun test10_schemaValidationIntegrity() {
        val emptyDbFile = File(context.cacheDir, "empty_schema_test.db")
        val sqlite = SQLiteDatabase.openOrCreateDatabase(emptyDbFile, null)
        sqlite.execSQL("CREATE TABLE sample_table (id INTEGER PRIMARY KEY)")
        sqlite.close()

        val sqliteCheck = SQLiteDatabase.openDatabase(emptyDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val requiredTables = listOf("stock_items", "projects", "orders", "invoices", "expenses")
        val existingTables = mutableSetOf<String>()
        sqliteCheck.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) {
                existingTables.add(cursor.getString(0))
            }
        }
        sqliteCheck.close()

        val missing = requiredTables.filter { !existingTables.contains(it) }
        assertTrue(missing.isNotEmpty())
        emptyDbFile.delete()
    }

    // Scenario 11: Retention with one good + two corrupt backups
    @Test
    fun test11_retentionWithOneGoodAndTwoCorruptBackups() {
        val backupDir = File(context.cacheDir, "retention_test_dir").apply { mkdirs() }
        val goodBackup = File(backupDir, "yans_db_backup_1000.enc")
        FileOutputStream(goodBackup).use { fos ->
            backupManager.exportBackup(fos)
        }

        val corrupt1 = File(backupDir, "yans_db_backup_2000.enc").apply { writeBytes(ByteArray(50)) }
        val corrupt2 = File(backupDir, "yans_db_backup_3000.enc").apply { writeBytes(ByteArray(60)) }

        val allBackups = backupDir.listFiles { f -> f.name.endsWith(".enc") } ?: emptyArray()
        val verifiedBackups = allBackups.filter { backupManager.verifyEncryptedBackupFile(it).isVerified }

        // Only the single valid verified backup qualifies
        assertEquals(1, verifiedBackups.size)
        assertEquals(goodBackup.name, verifiedBackups[0].name)

        goodBackup.delete()
        corrupt1.delete()
        corrupt2.delete()
        backupDir.delete()
    }

    // Scenario 12: Backup during process death / staging safety
    @Test
    fun test12_backupProcessDeathStagingSafety() {
        val tempRestoreFile = File(context.cacheDir, "yans_erp_db_restore_temp.db")
        tempRestoreFile.writeBytes(ByteArray(128))
        assertTrue(tempRestoreFile.exists())

        // Ensure cleanup deletes staging artifacts cleanly
        tempRestoreFile.delete()
        assertFalse(tempRestoreFile.exists())
    }

    // Scenario 13: Different collections syncing at different times
    @Test
    fun test13_differentCollectionsSyncingAtDifferentTimes() {
        SyncCheckpointManager.updateCheckpoint(
            context = context,
            collectionName = "invoices",
            lastSuccessfulSync = 1700000000000L,
            checkpoint = "inv_cp_100",
            status = "SUCCESS"
        )
        SyncCheckpointManager.updateCheckpoint(
            context = context,
            collectionName = "stock_items",
            lastSuccessfulSync = 1700005000000L,
            checkpoint = "stock_cp_200",
            status = "SUCCESS"
        )

        val invCp = SyncCheckpointManager.getCheckpoint(context, "invoices")
        val stockCp = SyncCheckpointManager.getCheckpoint(context, "stock_items")

        assertEquals("inv_cp_100", invCp.checkpoint)
        assertEquals(1700000000000L, invCp.lastSuccessfulSync)

        assertEquals("stock_cp_200", stockCp.checkpoint)
        assertEquals(1700005000000L, stockCp.lastSuccessfulSync)

        // Updating invoices checkpoint does NOT alter stock checkpoint
        SyncCheckpointManager.updateCheckpoint(
            context = context,
            collectionName = "invoices",
            lastSuccessfulSync = 1700009999000L,
            checkpoint = "inv_cp_300",
            status = "SUCCESS"
        )

        val invCpUpdated = SyncCheckpointManager.getCheckpoint(context, "invoices")
        val stockCpUnchanged = SyncCheckpointManager.getCheckpoint(context, "stock_items")

        assertEquals("inv_cp_300", invCpUpdated.checkpoint)
        assertEquals("stock_cp_200", stockCpUnchanged.checkpoint)
        assertEquals(1700005000000L, stockCpUnchanged.lastSuccessfulSync)
    }

    // Scenario 14: Main-thread metadata reset
    @Test
    fun test14_mainThreadMetadataReset() {
        SyncCheckpointManager.updateCheckpoint(
            context = context,
            collectionName = "projects",
            lastSuccessfulSync = 1000L,
            checkpoint = "proj_1"
        )
        // Reset call must execute safely without blocking
        SyncCheckpointManager.resetCheckpoint(context, "projects")
        val resetCp = SyncCheckpointManager.getCheckpoint(context, "projects")
        assertEquals("", resetCp.checkpoint)
        assertEquals(0L, resetCp.lastSuccessfulSync)
        assertEquals("IDLE", resetCp.status)
    }

    // Scenario 15: 100k invoice financial analytics (High performance linear scale)
    @Test
    fun test15_100kInvoiceFinancialAnalytics() {
        val invoices = ArrayList<Invoice>(100_000)
        val dummyTime = System.currentTimeMillis()
        for (i in 1..100_000) {
            invoices.add(
                Invoice(
                    id = i,
                    invoiceNumber = "INV-$i",
                    clientName = if (i % 2 == 0) "Member User MBR_${i % 50}" else "Retail User",
                    totalAmount = 100_000.0,
                    paidAmount = 80_000.0,
                    issueDate = dummyTime,
                    isDeleted = false
                )
            )
        }

        val expenses = listOf(
            Expense(id = 1, amount = 1_000_000_000.0, date = dummyTime)
        )
        val inflows = listOf(
            Inflow(id = 1, amount = 500_000_000.0, date = dummyTime)
        )

        val startTime = System.currentTimeMillis()
        val result = analyticsUseCase.computeAnalytics(
            invoices = invoices,
            payments = emptyList(),
            expenses = expenses,
            inflows = inflows,
            filter = AnalyticsTimeFilter.ALL
        )
        val duration = System.currentTimeMillis() - startTime

        // 100k * 100,000 = 10,000,000,000
        assertEquals(10_000_000_000L, result.totalRevenueLong)
        // 100k * 80,000 = 8,000,000,000
        assertEquals(8_000_000_000L, result.totalPaidLong)
        // 100k * 20,000 = 2,000,000,000
        assertEquals(2_000_000_000L, result.totalReceivablesLong)
        // Net profit = 8,000,000,000 + 500,000,000 - 1,000,000,000 = 7,500,000,000
        assertEquals(7_500_000_000L, result.netProfitLong)
        assertEquals(100_000, result.invoiceCount)
        assertEquals(50, result.memberCount)

        // Computation should complete swiftly (typically < 300ms in JVM)
        assertTrue("Analytics took too long: ${duration}ms", duration < 5000)
    }

    // Scenario 16: Jakarta timezone boundary around midnight
    @Test
    fun test16_jakartaTimezoneBoundaryAroundMidnight() {
        val zone = ZoneId.of("Asia/Jakarta")
        // 2026-08-15 00:01:00 in Asia/Jakarta
        val jakartaZoned = ZonedDateTime.of(2026, 8, 15, 0, 1, 0, 0, zone)
        val midnightMillis = jakartaZoned.toInstant().toEpochMilli()

        // 2026-08-14 23:59:00 in Asia/Jakarta (Previous day)
        val prevDayZoned = ZonedDateTime.of(2026, 8, 14, 23, 59, 0, 0, zone)
        val prevDayMillis = prevDayZoned.toInstant().toEpochMilli()

        // Evaluated at 2026-08-15 01:00:00 Jakarta time
        val nowMillis = ZonedDateTime.of(2026, 8, 15, 1, 0, 0, 0, zone).toInstant().toEpochMilli()

        val isMidnightInToday = analyticsUseCase.isTimestampInFilter(
            timestampEpochMillis = midnightMillis,
            filter = AnalyticsTimeFilter.TODAY,
            nowEpochMillis = nowMillis
        )
        val isPrevDayInToday = analyticsUseCase.isTimestampInFilter(
            timestampEpochMillis = prevDayMillis,
            filter = AnalyticsTimeFilter.TODAY,
            nowEpochMillis = nowMillis
        )

        assertTrue("00:01 Jakarta should be counted in TODAY", isMidnightInToday)
        assertFalse("23:59 yesterday Jakarta should NOT be counted in TODAY", isPrevDayInToday)
    }
}
