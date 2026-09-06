package com.yansproject.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * BitmapMemoryRecycler: Highly aggressive and defensive memory allocator and recycling utility
 * tailored for high-frequency bitmap generation (like digital receipt rendering) in YANSPROJECT.ID ERP.
 */
object BitmapMemoryRecycler {
    private const val TAG = "BitmapMemoryRecycler"

    // Thread-safe weak reference cache of reusable bitmaps to avoid GC churn & heap fragmentation
    private val reusableBitmaps = Collections.synchronizedSet(HashSet<WeakReference<Bitmap>>())

    /**
     * Safely attempts to recycle a bitmap, ensuring its native heap allocations are reclaimed.
     */
    @Synchronized
    fun recycle(bitmap: Bitmap?) {
        if (bitmap == null) return
        try {
            if (!bitmap.isRecycled) {
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
                Log.d(TAG, "Successfully recycled bitmap size: ${bitmap.width}x${bitmap.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recycle bitmap safely: ${e.message}", e)
        }
    }

    /**
     * Helper to convert bitmap into a compressed byte array and immediately recycle the source bitmap.
     * Guaranteed to release memory before returning byte array.
     */
    fun compressAndRecycle(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        return try {
            bitmap.compress(format, quality, outputStream)
            outputStream.toByteArray()
        } finally {
            try {
                outputStream.close()
            } catch (e: Exception) {
                // ignore close exception
            }
            recycle(bitmap)
        }
    }

    /**
     * Creates or reuses a Bitmap with precise dimensions and configuration.
     */
    @Synchronized
    fun createSafeBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        // Attempt to find a suitable un-recycled mutable bitmap from our pool
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

        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemory while creating bitmap (${width}x${height}). Purging reusable pool.", oom)
            clearReusablePool()
            Bitmap.createBitmap(width, height, config)
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
                candidate.recycle()
            }
            iterator.remove()
        }
        Log.d(TAG, "Pool cleared of all active bitmap handles.")
    }
}
