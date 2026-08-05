package com.yansproject.app.data

import android.os.SystemClock
import android.util.Log

data class PerformanceMetrics(
    val allocatedHeapMb: Double,
    val maxHeapMb: Double,
    val heapUsagePercent: Double,
    val executionTimeMs: Long
)

@PublishedApi
internal const val PERFORMANCE_MONITOR_TAG = "PerformanceMonitor"

/**
 * PerformanceMonitor: Lightweight performance and memory monitor designed to avoid runtime overhead.
 */
object PerformanceMonitor {

    inline fun <T> measureExecutionTime(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return try {
            block()
        } finally {
            val duration = SystemClock.elapsedRealtime() - start
            if (duration > 100) {
                Log.w(PERFORMANCE_MONITOR_TAG, "Performance warning: '$label' took ${duration}ms")
            } else {
                Log.d(PERFORMANCE_MONITOR_TAG, "Execution '$label' took ${duration}ms")
            }
        }
    }

    fun getMemorySnapshot(): PerformanceMetrics {
        val runtime = Runtime.getRuntime()
        val totalHeap = runtime.totalMemory().toDouble() / (1024 * 1024)
        val freeHeap = runtime.freeMemory().toDouble() / (1024 * 1024)
        val maxHeap = runtime.maxMemory().toDouble() / (1024 * 1024)
        val usedHeap = totalHeap - freeHeap
        val usagePercent = (usedHeap / maxHeap) * 100.0

        return PerformanceMetrics(
            allocatedHeapMb = usedHeap,
            maxHeapMb = maxHeap,
            heapUsagePercent = usagePercent,
            executionTimeMs = SystemClock.elapsedRealtime()
        )
    }
}
