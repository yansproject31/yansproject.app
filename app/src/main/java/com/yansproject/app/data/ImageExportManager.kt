package com.yansproject.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

class ImageExportException(message: String) : Exception(message)

data class ImageExportResult(
    val file: File,
    val width: Int,
    val height: Int,
    val fileSize: Long
)

/**
 * ImageExportManager: Manages bitmap export verification, bounds decoding, stream safety, and memory leak prevention.
 */
class ImageExportManager private constructor() {

    private val TAG = "ImageExportManager"
    private val exportManager = ExportManager.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: ImageExportManager? = null

        fun getInstance(): ImageExportManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ImageExportManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Exports a Bitmap to an image file with bitmap lifecycle safety, dimension verification, and stream completion check.
     */
    fun exportAndValidateImage(
        bitmap: Bitmap?,
        targetFile: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100,
        autoRecycle: Boolean = false
    ): Result<ImageExportResult> {
        // 1. Verify Bitmap Lifecycle & Validity
        if (bitmap == null) {
            return Result.failure(ImageExportException("Cannot export image: input bitmap is null."))
        }
        if (bitmap.isRecycled) {
            return Result.failure(ImageExportException("Cannot export image: bitmap has already been recycled."))
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return Result.failure(ImageExportException("Cannot export image: invalid bitmap dimensions (${bitmap.width}x${bitmap.height})."))
        }

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        try {
            // 2. Stream Completion & Export Execution
            val exportResult = exportManager.exportToFile(targetFile) { outputStream ->
                val compressed = bitmap.compress(format, quality, outputStream)
                if (!compressed) {
                    throw ImageExportException("Bitmap compression failed for format $format at quality $quality.")
                }
            }

            if (exportResult.isFailure) {
                return Result.failure(
                    exportResult.exceptionOrNull() ?: ImageExportException("Image stream write failed.")
                )
            }

            val file = exportResult.getOrThrow()

            // 3. Validate Output Size & Header Bounds Decoding without allocating pixels
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                file.delete()
                return Result.failure(ImageExportException("Corrupt image file: failed to decode valid header dimensions."))
            }

            if (options.outWidth != originalWidth || options.outHeight != originalHeight) {
                file.delete()
                return Result.failure(
                    ImageExportException("Image dimension mismatch: exported ${options.outWidth}x${options.outHeight}, expected ${originalWidth}x${originalHeight}.")
                )
            }

            // 4. Avoid Bitmap Memory Leaks
            if (autoRecycle && !bitmap.isRecycled) {
                bitmap.recycle()
                Log.d(TAG, "Bitmap successfully recycled after export.")
            }

            Log.i(TAG, "Image Export validated successfully: '${file.name}' (${options.outWidth}x${options.outHeight}, ${file.length()} bytes)")
            return Result.success(
                ImageExportResult(
                    file = file,
                    width = options.outWidth,
                    height = options.outHeight,
                    fileSize = file.length()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Image Export failed: ${e.message}", e)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            return Result.failure(
                if (e is ImageExportException) e else ImageExportException("Image export execution failed: ${e.message}")
            )
        }
    }
}
