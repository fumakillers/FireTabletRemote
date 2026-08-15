package com.fumakillers.fireremoteserver.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class AccessibilityScreenshotProviderTest {
    @Test
    fun rejectsOverlappingPreviewWithoutQueueingAnotherScreenshot() {
        val gateway = HoldingScreenshotGateway()
        val provider = AccessibilityScreenshotProvider(gateway) { 0L }
        val firstResults = mutableListOf<PreviewResult>()
        val secondResults = mutableListOf<PreviewResult>()

        try {
            provider.capture(firstResults::add)
            provider.capture(secondResults::add)

            assertEquals(1, gateway.captureCount)
            assertTrue(firstResults.isEmpty())
            assertEquals(
                PreviewResult.Error("Preview capture is already in progress"),
                secondResults.single(),
            )
        } finally {
            provider.close()
        }
    }

    private class HoldingScreenshotGateway : ScreenshotGateway {
        var captureCount = 0

        override fun capture(
            executor: Executor,
            callback: (ScreenshotCaptureResult) -> Unit,
        ) {
            captureCount++
        }
    }
}
