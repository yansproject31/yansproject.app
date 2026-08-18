package com.yansproject.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

data class NotificationDedupeRecord(
    val notificationEventId: String,
    val recipientUid: String,
    val createdAt: Long,
    val deliveredAt: Long,
    val expiresAt: Long
)

class NotificationDedupeDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "yans_notification_dedupe_durable.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notification_dedupe (
                notification_event_id TEXT NOT NULL,
                recipient_uid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                delivered_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                PRIMARY KEY (notification_event_id, recipient_uid)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Upgrades handled gracefully
    }
}

/**
 * Durable storage for notification deduplication.
 * Replaces ephemeral SharedPreferences StringSet.
 * Enforces user-scoped, deterministic deduplication that survives app restarts.
 */
class NotificationDedupeStore private constructor(context: Context) {
    private val dbHelper = NotificationDedupeDatabaseHelper(context.applicationContext)
    private val TAG = "NotificationDedupeStore"

    companion object {
        @Volatile
        private var INSTANCE: NotificationDedupeStore? = null

        fun getInstance(context: Context): NotificationDedupeStore {
            return INSTANCE ?: synchronized(this) {
                val instance = NotificationDedupeStore(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun isDelivered(notificationEventId: String, recipientUid: String = "ALL"): Boolean {
        if (notificationEventId.isBlank()) return false
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "notification_dedupe",
                arrayOf("notification_event_id"),
                "notification_event_id = ? AND (recipient_uid = ? OR recipient_uid = 'ALL' OR ? = 'ALL')",
                arrayOf(notificationEventId, recipientUid, recipientUid),
                null, null, null
            )
            val exists = cursor.use { it.moveToFirst() }
            exists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking dedupe for $notificationEventId: ${e.message}")
            false
        }
    }

    /**
     * Atomically claims delivery identity "recipientUid + notificationId".
     * Returns true if successfully inserted (first claim), false if already claimed/delivered.
     * Guarantees two workers never dispatch the same notification twice.
     */
    fun tryClaimDeliveryAtomic(
        notificationEventId: String,
        recipientUid: String = "ALL",
        createdAt: Long = System.currentTimeMillis(),
        deliveredAt: Long = System.currentTimeMillis(),
        ttlMillis: Long = 30 * 24 * 3600 * 1000L
    ): Boolean {
        if (notificationEventId.isBlank()) return false
        val uid = if (recipientUid.isBlank()) "ALL" else recipientUid
        return try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("notification_event_id", notificationEventId)
                put("recipient_uid", uid)
                put("created_at", createdAt)
                put("delivered_at", deliveredAt)
                put("expires_at", deliveredAt + ttlMillis)
            }
            val rowId = db.insertWithOnConflict(
                "notification_dedupe",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
            )
            rowId != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error in tryClaimDeliveryAtomic for $notificationEventId: ${e.message}")
            false
        }
    }

    fun markDelivered(
        notificationEventId: String,
        recipientUid: String = "ALL",
        createdAt: Long = System.currentTimeMillis(),
        deliveredAt: Long = System.currentTimeMillis(),
        ttlMillis: Long = 30 * 24 * 3600 * 1000L // 30 days default expiration
    ) {
        if (notificationEventId.isBlank()) return
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("notification_event_id", notificationEventId)
                put("recipient_uid", if (recipientUid.isBlank()) "ALL" else recipientUid)
                put("created_at", createdAt)
                put("delivered_at", deliveredAt)
                put("expires_at", deliveredAt + ttlMillis)
            }
            db.insertWithOnConflict(
                "notification_dedupe",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error marking delivered for $notificationEventId: ${e.message}")
        }
    }

    fun purgeExpiredRecords(now: Long = System.currentTimeMillis()): Int {
        return try {
            val db = dbHelper.writableDatabase
            db.delete("notification_dedupe", "expires_at < ?", arrayOf(now.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "Error purging expired dedupe records: ${e.message}")
            0
        }
    }
}
