package com.yansproject.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.Collections

enum class BitmapOwnership {
    CALLER_OWNED,
    TRANSFERRED_TO_RECYCLER
}

class BitmapCompressionException(message: String) : Exception(message)
class BitmapAllocationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * BitmapMemoryRecycler: Highly defensive memory allocator and recycling utility
 * tailored for high-frequency bitmap generation in YANSPROJECT.ID ERP.
 */
object BitmapMemoryRecycler {
    private const val TAG = "BitmapMemoryRecycler"

    // Thread-safe weak reference cache of reusable bitmaps
    private val reusableBitmaps = Collections.synchronizedSet(HashSet<WeakReference<Bitmap>>())

    /**
     * Safely attempts to recycle a bitmap, ensuring its native heap allocations are reclaimed.
     * Does NOT access dimensions after recycle().
     */
    @Synchronized
    fun recycle(bitmap: Bitmap?, ownership: BitmapOwnership = BitmapOwnership.TRANSFERRED_TO_RECYCLER) {
        if (bitmap == null) return
        if (ownership == BitmapOwnership.CALLER_OWNED) {
            Log.d(TAG, "Skipping recycle: Bitmap ownership retained by caller.")
            return
        }

        try {
            if (!bitmap.isRecycled) {
                // Capture dimensions BEFORE recycle
                val w = bitmap.width
                val h = bitmap.height

                // Remove reference from reusable cache if present
                val iterator = reusableBitmaps.iterator()
                while (iterator.hasNext()) {
                    val ref = iterator.next()
                    val candidate = ref.get()
                    if (candidate == null || candidate === bitmap || candidate.isRecycled) {
                        iterator.remove()
                    }
                }

                bitmap.recycle()
                Log.d(TAG, "Successfully recycled bitmap size: ${w}x${h}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recycle bitmap safely: ${e.message}", e)
        }
    }

    /**
     * Compresses bitmap to byte array with strict success verification and explicit ownership handling.
     * Throws BitmapCompressionException if compress() fails.
     */
    fun compressAndRecycle(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        ownership: BitmapOwnership = BitmapOwnership.TRANSFERRED_TO_RECYCLER
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var compressedOk = false
        return try {
            compressedOk = bitmap.compress(format, quality, outputStream)
            if (!compressedOk) {
                throw BitmapCompressionException("Bitmap compress() returned false (compression failed for format=$format, quality=$quality)")
            }
            outputStream.toByteArray()
        } finally {
            try {
                outputStream.close()
            } catch (e: Exception) {
                // ignore close exception
            }
            if (ownership == BitmapOwnership.TRANSFERRED_TO_RECYCLER) {
                recycle(bitmap, ownership)
            }
        }
    }

    /**
     * Creates or reuses a Bitmap with graceful OOM handling and fallback downscaling.
     */
    @Synchronized
    fun createSafeBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        // 1. Attempt reuse from pool
        val iterator = reusableBitmaps.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val candidate = ref.get()
            if (candidate == null || candidate.isRecycled) {
                iterator.remove()
                continue
            }
            if (candidate.isMutable && candidate.width == width && candidate.height == height && candidate.config == config) {
                iterator.remove()
                Log.d(TAG, "Reusing pooled bitmap with size: ${width}x${height}")
                candidate.eraseColor(android.graphics.Color.TRANSPARENT)
                return candidate
            }
        }

        // 2. Fresh allocation with defensive OOM recovery
        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemory while creating bitmap (${width}x${height}). Purging reusable pool...", oom)
            clearReusablePool()
            System.gc()

            // Graceful fallback: try RGB_565 or half resolution rather than repeating identical failing allocation
            try {
                val fallbackConfig = if (config == Bitmap.Config.ARGB_8888) Bitmap.Config.RGB_565 else config
                Log.w(TAG, "Attempting fallback bitmap creation with config $fallbackConfig")
                Bitmap.createBitmap(width, height, fallbackConfig)
            } catch (secondOom: OutOfMemoryError) {
                Log.e(TAG, "Secondary OutOfMemory creating bitmap. Attempting downscaled half-resolution bitmap.", secondOom)
                try {
                    val halfW = (width / 2).coerceAtLeast(1)
                    val halfH = (height / 2).coerceAtLeast(1)
                    Bitmap.createBitmap(halfW, halfH, Bitmap.Config.RGB_565)
                } catch (thirdOom: OutOfMemoryError) {
                    throw BitmapAllocationException("Failed to allocate bitmap (${width}x${height}) after pool purge and downscale fallbacks", thirdOom)
                }
            }
        }
    }

    /**
     * Clear all referenced bitmaps in our pool.
     */
    @Synchronized
    fun clearReusablePool() {
        val iterator = reusableBitmaps.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val candidate = ref.get()
            if (candidate != null && !candidate.isRecycled) {
                val w = candidate.width
                val h = candidate.height
                candidate.recycle()
                Log.d(TAG, "Purged pooled bitmap: ${w}x${h}")
            }
            iterator.remove()
        }
        Log.d(TAG, "Pool cleared of all active bitmap handles.")
    }
}

