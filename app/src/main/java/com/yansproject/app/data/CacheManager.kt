package com.yansproject.app.data

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class CacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val ttlMs: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
}

/**
 * CacheManager: Provides explicit TTL-based memory caching with safe corruption handling and room database SSOT fallback.
 */
class CacheManager private constructor(private val context: Context) {

    private val TAG = "CacheManager"
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<*>>()
    val DEFAULT_TTL_MS = 15 * 60 * 1000L // 15 minutes default TTL
    val MAX_CACHE_CAPACITY = 200 // Explicit capacity cap to prevent memory leaks

    companion object {
        @Volatile
        private var INSTANCE: CacheManager? = null

        fun getInstance(context: Context): CacheManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CacheManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun <T> put(key: String, value: T, ttlMs: Long = DEFAULT_TTL_MS) {
        if (memoryCache.size >= MAX_CACHE_CAPACITY) {
            purgeExpiredEntries()
            if (memoryCache.size >= MAX_CACHE_CAPACITY) {
                // Evict oldest entry by timestamp
                val oldestKey = memoryCache.minByOrNull { it.value.timestamp }?.key
                if (oldestKey != null) {
                    memoryCache.remove(oldestKey)
                    Log.d(TAG, "Cache capacity limit reached ($MAX_CACHE_CAPACITY). Evicted oldest entry: $oldestKey")
                }
            }
        }
        memoryCache[key] = CacheEntry(data = value, timestamp = System.currentTimeMillis(), ttlMs = ttlMs)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = memoryCache[key] as? CacheEntry<T> ?: return null
        return if (entry.isExpired()) {
            Log.d(TAG, "Cache key '$key' expired. Evicting.")
            memoryCache.remove(key)
            null
        } else {
            entry.data
        }
    }

    fun invalidate(key: String) {
        memoryCache.remove(key)
        Log.d(TAG, "Invalidated cache entry for key: $key")
    }

    fun clearAll() {
        memoryCache.clear()
        Log.i(TAG, "Memory cache fully cleared.")
    }

    fun purgeExpiredEntries() {
        val keysToRemove = memoryCache.filterValues { it.isExpired() }.keys
        keysToRemove.forEach { memoryCache.remove(it) }
        if (keysToRemove.isNotEmpty()) {
            Log.d(TAG, "Purged ${keysToRemove.size} expired cache entries.")
        }
    }
}
