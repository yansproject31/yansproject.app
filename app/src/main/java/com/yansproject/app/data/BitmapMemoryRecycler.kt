package com.yansproject.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.Collections

object BitmapMemoryRecycler {
    private const val TAG = "BitmapMemoryRecycler"

    private val reusableBitmaps = Collections.synchronizedSet(HashSet<WeakReference<Bitmap>>())

    @Synchronized
    fun recycle(bitmap: Bitmap?) {
        if (bitmap == null) return
        try {
            if (!bitmap.isRecycled) {
                if (bitmap.isMutable) {
                    reusableBitmaps.add(WeakReference(bitmap))
                }
                bitmap.recycle()
                Log.d(TAG, "Successfully recycled bitmap size: ${bitmap.width}x${bitmap.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recycle bitmap safely", e)
        }
    }

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

    @Synchronized
    fun createSafeBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
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

        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory()
        val neededBytes = width * height * 4
        
        if (freeMemory < neededBytes * 2L) {
            Log.w(TAG, "Low system heap memory! Triggering aggressive GC and clean-up before allocation.")
            System.gc()
        }

        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Critical: OutOfMemory while creating safe bitmap. Purging all caches.", oom)
            clearReusablePool()
            System.gc()
            // PERBAIKAN FATAL: Bungkus panggilan kedua dengan try-catch & fallback 1x1 bitmap agar tidak Force Close!
            try {
                Bitmap.createBitmap(width, height, config)
            } catch (fatalOom: OutOfMemoryError) {
                Log.e(TAG, "Fatal OOM. Returning fallback 1x1 empty bitmap to prevent crash.", fatalOom)
                Bitmap.createBitmap(1, 1, config)
            }
        }
    }

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