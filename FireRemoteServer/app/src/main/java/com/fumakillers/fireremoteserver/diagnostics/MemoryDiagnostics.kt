package com.fumakillers.fireremoteserver.diagnostics

import android.os.Debug
import com.fumakillers.fireremoteserver.logging.RemoteLogger

data class MemorySnapshot(
    val javaUsedBytes: Long,
    val javaTotalBytes: Long,
    val javaMaxBytes: Long,
    val nativeUsedBytes: Long,
    val totalPssKb: Long,
) {
    fun format(): String =
        "javaUsed=${javaUsedBytes.toMiB()}MB " +
            "javaTotal=${javaTotalBytes.toMiB()}MB " +
            "javaMax=${javaMaxBytes.toMiB()}MB " +
            "native=${nativeUsedBytes.toMiB()}MB pss=${totalPssKb / 1024}MB"

    private fun Long.toMiB(): Long = this / (1024 * 1024)
}

object MemoryDiagnostics {
    fun capture(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            javaUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            javaTotalBytes = runtime.totalMemory(),
            javaMaxBytes = runtime.maxMemory(),
            nativeUsedBytes = Debug.getNativeHeapAllocatedSize(),
            totalPssKb = Debug.getPss(),
        )
    }

    fun logCurrentUsage() {
        try {
            RemoteLogger.debug(TAG, capture().format())
        } catch (error: RuntimeException) {
            RemoteLogger.warn(TAG, "Could not collect memory diagnostics", error)
        }
    }

    private const val TAG = "FireRemoteMemory"
}
