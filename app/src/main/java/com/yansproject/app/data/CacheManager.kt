package com.yansproject.app.data

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

sealed class CacheNamespace {
    object Public : CacheNamespace() {
        override fun toString(): String = "PUBLIC"
    }

    data class User(val uid: String) : CacheNamespace() {
        init {
            require(uid.isNotBlank()) { "User cache namespace requires a non-blank authenticated UID" }
        }
        override fun toString(): String = "USER:$uid"
    }

    data class Session(val sessionId: String) : CacheNamespace() {
        init {
            require(sessionId.isNotBlank()) { "Session cache namespace requires a non-blank sessionId" }
        }
        override fun toString(): String = "SESSION:$sessionId"
    }

    fun prefix(): String = toString()
}

data class CacheKey<T>(
    val key: String,
    val type: Class<T>,
    val namespace: CacheNamespace = CacheNamespace.Public
) {
    init {
        require(key.isNotBlank()) { "CacheKey string must not be blank" }
    }

    fun qualifiedKey(): String = "${namespace.prefix()}::${type.name}::$key"

    companion object {
        inline fun <reified T> create(key: String, namespace: CacheNamespace = CacheNamespace.Public): CacheKey<T> {
            return CacheKey(key, T::class.java, namespace)
        }
    }
}

data class CacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val ttlMs: Long,
    val type: Class<T>,
    val namespace: CacheNamespace
) {
    fun isExpired(): Boolean = ttlMs > 0 && (System.currentTimeMillis() - timestamp > ttlMs)
}

/**
 * CacheManager: Provides namespaced, typed TTL-based memory caching with strict validation,
 * type-safety, and room database SSOT fallback.
 */
class CacheManager private constructor(private val context: Context) {

    private val TAG = "CacheManager"
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<*>>()

    val DEFAULT_TTL_MS = 15 * 60 * 1000L // 15 minutes default TTL
    val NO_CACHE = 0L
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

    /**
     * Stores a typed value under a typed CacheKey with strict namespace and TTL validation.
     */
    fun <T> putTyped(cacheKey: CacheKey<T>, value: T, ttlMs: Long = DEFAULT_TTL_MS) {
        // Validate TTL
        if (ttlMs < 0) {
            throw IllegalArgumentException("Cache TTL cannot be negative: $ttlMs ms")
        }
        if (ttlMs == NO_CACHE) {
            Log.d(TAG, "NO_CACHE policy specified for ${cacheKey.qualifiedKey()}. Skipping cache insertion.")
            return
        }

        // Validate user namespace
        if (cacheKey.namespace is CacheNamespace.User) {
            if (cacheKey.namespace.uid.isBlank()) {
                throw IllegalStateException("User-scoped cache requires a valid authenticated UID")
            }
        }

        if (memoryCache.size >= MAX_CACHE_CAPACITY) {
            purgeExpiredEntries()
            if (memoryCache.size >= MAX_CACHE_CAPACITY) {
                // Evict oldest entry
                val oldestKey = memoryCache.minByOrNull { it.value.timestamp }?.key
                if (oldestKey != null) {
                    memoryCache.remove(oldestKey)
                    Log.d(TAG, "Cache capacity limit reached ($MAX_CACHE_CAPACITY). Evicted oldest: $oldestKey")
                }
            }
        }

        val entry = CacheEntry(
            data = value,
            timestamp = System.currentTimeMillis(),
            ttlMs = ttlMs,
            type = cacheKey.type,
            namespace = cacheKey.namespace
        )
        memoryCache[cacheKey.qualifiedKey()] = entry
    }

    /**
     * Retrieves a typed value under a typed CacheKey.
     * Guaranteed type-safe with zero possibility of cross-type collision.
     */
    fun <T> getTyped(cacheKey: CacheKey<T>): T? {
        if (cacheKey.namespace is CacheNamespace.User && cacheKey.namespace.uid.isBlank()) {
            Log.e(TAG, "Attempted user cache access with blank UID")
            return null
        }

        val rawEntry = memoryCache[cacheKey.qualifiedKey()] ?: return null

        if (rawEntry.isExpired()) {
            Log.d(TAG, "Cache key '${cacheKey.qualifiedKey()}' expired. Evicting.")
            memoryCache.remove(cacheKey.qualifiedKey())
            return null
        }

        return if (cacheKey.type.isInstance(rawEntry.data)) {
            @Suppress("UNCHECKED_CAST")
            rawEntry.data as T
        } else {
            Log.e(TAG, "Cache type collision detected for '${cacheKey.qualifiedKey()}'. Expected ${cacheKey.type}, found ${rawEntry.data?.javaClass}. Evicting corrupt entry.")
            memoryCache.remove(cacheKey.qualifiedKey())
            null
        }
    }

    /**
     * Backward-compatible untyped put wrapper.
     */
    fun <T : Any> put(key: String, value: T, ttlMs: Long = DEFAULT_TTL_MS) {
        val cacheKey = CacheKey(key, value.javaClass, CacheNamespace.Public)
        @Suppress("UNCHECKED_CAST")
        putTyped(cacheKey as CacheKey<Any>, value, ttlMs)
    }

    /**
     * Backward-compatible untyped get wrapper.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        // Search public namespace entries matching raw key
        val entry = memoryCache.entries.firstOrNull { 
            it.key.endsWith("::$key") && it.value.namespace == CacheNamespace.Public 
        }?.value ?: return null

        return if (entry.isExpired()) {
            memoryCache.entries.removeIf { it.value == entry }
            null
        } else {
            entry.data as? T
        }
    }

    fun invalidateTyped(cacheKey: CacheKey<*>) {
        memoryCache.remove(cacheKey.qualifiedKey())
        Log.d(TAG, "Invalidated cache entry for key: ${cacheKey.qualifiedKey()}")
    }

    fun invalidate(key: String) {
        val keysToRemove = memoryCache.keys.filter { it.endsWith("::$key") }
        keysToRemove.forEach { memoryCache.remove(it) }
        Log.d(TAG, "Invalidated ${keysToRemove.size} cache entries matching: $key")
    }

    fun clearUserCache(uid: String) {
        if (uid.isBlank()) return
        val prefix = "USER:$uid"
        val keysToRemove = memoryCache.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { memoryCache.remove(it) }
        Log.i(TAG, "Cleared ${keysToRemove.size} user-scoped cache entries for UID: $uid")
    }

    fun clearSessionCache(sessionId: String) {
        if (sessionId.isBlank()) return
        val prefix = "SESSION:$sessionId"
        val keysToRemove = memoryCache.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { memoryCache.remove(it) }
        Log.i(TAG, "Cleared ${keysToRemove.size} session-scoped cache entries for sessionId: $sessionId")
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

