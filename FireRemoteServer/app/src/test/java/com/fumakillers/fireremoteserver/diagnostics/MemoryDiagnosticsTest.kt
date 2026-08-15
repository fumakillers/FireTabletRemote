package com.fumakillers.fireremoteserver.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryDiagnosticsTest {
    @Test
    fun formatsMemoryValuesAsMegabytes() {
        val mebibyte = 1024L * 1024L
        val snapshot = MemorySnapshot(
            javaUsedBytes = 42 * mebibyte,
            javaTotalBytes = 64 * mebibyte,
            javaMaxBytes = 256 * mebibyte,
            nativeUsedBytes = 12 * mebibyte,
            totalPssKb = 80 * 1024,
        )

        assertEquals(
            "javaUsed=42MB javaTotal=64MB javaMax=256MB native=12MB pss=80MB",
            snapshot.format(),
        )
    }
}
